"""Audit two completed 50-item/2-epoch benchmark artifacts; stdlib only, no training.

Usage: python scripts/compare_global_benchmarks.py BEFORE_RUN AFTER_RUN --output OUTPUT.json
Prediction/metric tolerance is fixed before optimization at 1e-9 sales units.
"""
import argparse
import csv
import json
import math
from datetime import date, timedelta
from collections import Counter, defaultdict
from pathlib import Path

TOLERANCE = 1e-9


def read_json(run, name):
    return json.loads((run / name).read_text(encoding="utf-8"))


def read_csv(run, name):
    with (run / name).open(encoding="utf-8", newline="") as stream:
        return list(csv.DictReader(stream))


def audit(run):
    manifest = read_json(run, "run.json")
    assert manifest["status"] == "SUCCESS" and manifest["benchmark"] is True
    assert manifest["itemIds"] == list(range(1, 51)) and manifest["storeId"] == 1
    assert manifest["seeds"] == [42] and manifest["maxEpochs"] == 2
    assert manifest["batchSize"] == 64 and "JCublasBackend" in manifest["backend"]
    assert manifest["testUsedForSelection"] is False and manifest["testEvaluated"] is False
    profile = read_json(run, "performance-profile.json")
    assert profile["counts"]["trainWindows"] == 52300
    assert profile["counts"]["validationOriginsPerEvaluation"] == 18000
    assert profile["counts"]["train.batches"] == 1636
    cached = profile["counts"].get("validation.cachedCheckpointResults", 0)
    assert cached in (0, 1)
    assert profile["counts"]["validation.evaluations"] == 3 - cached
    assert profile["counts"]["validation.forecasts"] == 126000 * (3 - cached)
    assert profile["counts"]["modelOutputCalls"] == 1974 * (3 - cached)
    predictions = read_csv(run, "global-validation-predictions.csv")
    assert len(predictions) == 126000
    keys = [(p["store"], p["item"], p["seed"], p["model"], p["origin_date"], p["target_date"], p["horizon"]) for p in predictions]
    assert len(set(keys)) == len(keys)
    assert set(Counter((p["item"], p["horizon"]) for p in predictions).values()) == {360}
    assert len(Counter((p["item"], p["origin_date"]) for p in predictions)) == 18000
    for p in predictions:
        assert all(math.isfinite(float(p[k])) for k in ("actual_sales", "weekly_baseline", "predicted_residual", "predicted_sales"))
        assert date.fromisoformat(p["target_date"]) == date.fromisoformat(p["origin_date"]) + timedelta(days=int(p["horizon"]))
        assert date.fromisoformat(p["target_date"]).year == 2016
    scales = read_csv(run, "global-series-scaling.csv")
    assert len(scales) == 50 and {int(s["item"]) for s in scales} == set(range(1, 51))
    assert all(int(s["train_window_count"]) == 1046 and int(s["validation_window_count"]) == 360 for s in scales)
    reconstruction = max(abs(float(p["predicted_sales"]) - float(p["weekly_baseline"]) - float(p["predicted_residual"])) for p in predictions)
    assert reconstruction < TOLERANCE
    errors = defaultdict(list)
    for p in predictions:
        errors[p["item"]].append(float(p["predicted_sales"]) - float(p["actual_sales"]))
    scores = {item: (sum(map(abs, e)) / len(e), math.sqrt(sum(x*x for x in e) / len(e))) for item, e in errors.items()}
    metrics = read_csv(run, "global-item-seed-metrics.csv")
    assert len(metrics) == 50 and {int(m["forecast_count"]) for m in metrics} == {2520}
    recompute = max(abs(scores[m["item"]][i] - float(m[name])) for m in metrics for i, name in enumerate(("mae", "rmse")))
    assert recompute < TOLERANCE
    epochs = read_csv(run, "global-epoch-history.csv")
    assert len(epochs) == 2 and sum(e["best_flag"] == "true" for e in epochs) == 1
    best = next(int(e["epoch"]) for e in epochs if e["best_flag"] == "true")
    assert {int(m["best_epoch"]) for m in metrics} == {best}
    assert best == int(min(epochs, key=lambda e: float(e["validation_macro_rmse"]))["epoch"])
    aggregates = {"macroMae": sum(s[0] for s in scores.values()) / 50,
                  "macroRmse": sum(s[1] for s in scores.values()) / 50,
                  "pooledMae": sum(abs(e) for es in errors.values() for e in es) / 126000,
                  "pooledRmse": math.sqrt(sum(e*e for es in errors.values() for e in es) / 126000)}
    selected = next(e for e in epochs if int(e["epoch"]) == best)
    for key, column in (("macroMae", "validation_macro_mae"), ("macroRmse", "validation_macro_rmse"),
                        ("pooledMae", "validation_pooled_mae"), ("pooledRmse", "validation_pooled_rmse")):
        assert abs(aggregates[key] - float(selected[column])) < TOLERANCE
    return manifest, profile, predictions, keys, metrics, epochs, best, aggregates, recompute, reconstruction


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("before", type=Path); parser.add_argument("after", type=Path)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    b, a = audit(args.before), audit(args.after)
    ignored = {"runId", "startedAt", "updatedAt"}
    assert {k:v for k,v in b[0].items() if k not in ignored} == {k:v for k,v in a[0].items() if k not in ignored}
    assert b[1]["evidence"] == a[1]["evidence"], "Window/scaler hashes changed"
    computational = {"validation.evaluations", "validation.forecasts", "modelOutputCalls", "validation.cachedCheckpointResults"}
    assert {k:v for k,v in b[1]["counts"].items() if k not in computational} == {k:v for k,v in a[1]["counts"].items() if k not in computational}, "Population/training counts changed"
    assert read_csv(args.before,"global-series-scaling.csv") == read_csv(args.after,"global-series-scaling.csv")
    assert b[3] == a[3], "Ordered target keys changed"
    for x,y in zip(b[2],a[2]):
        assert x["actual_sales"] == y["actual_sales"] and x["weekly_baseline"] == y["weekly_baseline"]
    differences = [abs(float(x["predicted_sales"]) - float(y["predicted_sales"])) for x,y in zip(b[2],a[2])]
    max_diff, mean_diff = max(differences), sum(differences)/len(differences)
    metric_diff = {key: abs(b[7][key]-a[7][key]) for key in b[7]}
    per_item_diff = max(abs(float(x[k])-float(y[k])) for x,y in zip(b[4],a[4]) for k in ("mae","rmse"))
    epoch_diff = max(abs(float(x[k])-float(y[k])) for x,y in zip(b[5],a[5]) for k in ("train_loss_scaled","validation_macro_mae","validation_macro_rmse","validation_pooled_mae","validation_pooled_rmse"))
    assert max_diff <= TOLERANCE and max(metric_diff.values()) <= TOLERANCE
    assert per_item_diff <= TOLERANCE and epoch_diff <= TOLERANCE and b[6] == a[6]
    phases = ("data.total", "train.total", "validation.total", "artifact.total")
    timing = {key: {"before": b[1]["seconds"][key], "after": a[1]["seconds"][key], "speedup": b[1]["seconds"][key]/a[1]["seconds"][key]} for key in phases}
    timing["total"] = {"before":b[1]["totalSeconds"],"after":a[1]["totalSeconds"],"speedup":b[1]["totalSeconds"]/a[1]["totalSeconds"]}
    result = dict(status="PASS", before=str(args.before), after=str(args.after), tolerance=TOLERANCE,
                  maxPredictionAbsDiff=max_diff, meanPredictionAbsDiff=mean_diff, metricAbsDiff=metric_diff,
                  maxPerItemMetricAbsDiff=per_item_diff,maxEpochMetricAbsDiff=epoch_diff,weeklyDiff=0,
                  forecastCount=126000,bestEpochBefore=b[6],bestEpochAfter=a[6],
                  recomputeMaxBefore=b[8],recomputeMaxAfter=a[8],reconstructionMaxBefore=b[9],reconstructionMaxAfter=a[9],
                  windowHashes=b[1]["evidence"],countsBefore=b[1]["counts"],countsAfter=a[1]["counts"],timing=timing)
    args.output.write_text(json.dumps(result,indent=2)+"\n",encoding="utf-8")
    print(json.dumps(result,indent=2))


if __name__ == "__main__":
    main()
