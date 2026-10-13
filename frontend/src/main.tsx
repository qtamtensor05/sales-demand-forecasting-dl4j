import React, { useEffect, useState } from "react";
import { createRoot } from "react-dom/client";
import "./style.css";
import "./forecast.css";
import "./analysis.css";
import "./ingestion.css";

type Json = Record<string, any>;
const get = async (url: string) => {
  const r = await fetch(url);
  if (!r.ok) throw new Error(await r.text());
  return r.json();
};

function App() {
  const [tab, setTab] = useState("Tổng quan");
  const [health, setHealth] = useState<Json | null>(null);
  const [items, setItems] = useState<Json[]>([]);
  const [pipes, setPipes] = useState<Json[]>([]);
  const [models, setModels] = useState<Json[]>([]);
  const [bench, setBench] = useState<Json[]>([]);
  const [item, setItem] = useState(1);
  const [origin, setOrigin] = useState("2017-06-30");
  const [forecast, setForecast] = useState<Json | null>(null);
  const [sales, setSales] = useState<Json[]>([]);
  const [monthlyDemand, setMonthlyDemand] = useState<Json[]>([]);
  const [period, setPeriod] = useState<"day" | "week" | "month">("month");
  const [rankingMonth, setRankingMonth] = useState("2017-01");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const [uploadFile, setUploadFile] = useState<File | null>(null);
  const [uploadRun, setUploadRun] = useState<Json | null>(null);
  const [uploading, setUploading] = useState(false);
  const load = () =>
    Promise.all([
      get("/api/health").then(setHealth),
      get("/api/items").then(setItems),
      get("/api/pipelines").then(setPipes),
      get("/api/models").then(setModels),
      get("/api/benchmarks").then(setBench),
    ]).catch((e) => setError(String(e)));
  useEffect(() => {
    load();
  }, []);
  useEffect(() => {
    if (!uploadRun?.id || ["COMPLETED", "FAILED"].includes(uploadRun.status)) return;
    const timer = window.setInterval(() => {
      get(`/api/pipelines/ingest/${uploadRun.id}`).then((run) => {
        setUploadRun(run);
        if (["COMPLETED", "FAILED"].includes(run.status)) load();
      }).catch((e) => setError(String(e)));
    }, 2000);
    return () => window.clearInterval(timer);
  }, [uploadRun?.id, uploadRun?.status]);
  useEffect(() => {
    if (tab === "Phân tích")
      Promise.all([
        get(
          `/api/items/${item}/sales?from=2017-01-01&to=2017-12-31&size=400`,
        ).then((x) => setSales(x.content)),
        get("/api/analytics/monthly-demand?year=2017&limit=5").then(
          setMonthlyDemand,
        ),
      ]).catch((e) => setError(String(e)));
  }, [tab, item]);
  const run = async () => {
    setBusy(true);
    setError("");
    try {
      const r = await fetch("/api/forecasts", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ itemId: item, originDate: origin }),
      });
      const x = await r.json();
      if (!r.ok) throw new Error(x.message || "Forecast failed");
      setForecast(x);
    } catch (e) {
      setError(String(e));
    } finally {
      setBusy(false);
    }
  };
  const upload = async () => {
    if (!uploadFile) return;
    setUploading(true); setError("");
    try {
      const body = new FormData(); body.append("file", uploadFile);
      const response = await fetch("/api/pipelines/ingest", { method: "POST", body });
      const result = await response.json();
      if (!response.ok) throw new Error(result.message || "Không thể tải file");
      setUploadRun(result); load();
    } catch (e) { setError(String(e)); }
    finally { setUploading(false); }
  };
  const retryUpload = async () => {
    if (!uploadRun?.id) return;
    const response = await fetch(`/api/pipelines/ingest/${uploadRun.id}/retry`, { method: "POST" });
    const result = await response.json();
    if (!response.ok) { setError(result.message || "Không thể thử lại"); return; }
    setUploadRun({ ...result, status: "ACCEPTED" });
  };
  const tabs = [
    "Tổng quan",
    "Data Lake",
    "Nhập dữ liệu",
    "Phân tích",
    "Dự báo",
    "Đánh giá",
    "Spark Benchmark",
  ];
  return (
    <div>
      <aside>
        <div className="brand">
          DEMAND<span>LAB</span>
        </div>
        {tabs.map((x) => (
          <button
            className={tab === x ? "active" : ""}
            onClick={() => setTab(x)}
          >
            {x}
          </button>
        ))}
        <small>
          Store 1 · Local MVP
          <br />
          Miễn phí · Docker Compose
        </small>
      </aside>
      <main>
        <header>
          <div>
            <p className="eyebrow">BIG DATA FORECASTING</p>
            <h1>{tab}</h1>
          </div>
          <span className={health ? "ok" : "bad"}>
            ● {health ? "Hệ thống hoạt động" : "Đang kết nối"}
          </span>
        </header>
        {error && <div className="error">{error}</div>}
        {tab === "Tổng quan" && (
          <>
            <div className="notice">
              <strong>Bắt đầu ở đâu?</strong>
              <span>
                Vào mục <b>Dự báo</b>, chọn một mã sản phẩm, chọn ngày bắt đầu
                rồi nhấn <b>Dự báo 7 ngày tiếp theo</b>.
              </span>
            </div>
            <section className="cards">
              <Card label="Bản ghi bán hàng" value="91.300" />
              <Card
                label="Mã sản phẩm ẩn danh"
                value={String(items.length || 50)}
              />
              <Card label="Khoảng dữ liệu" value="2013—2017" />
              <Card
                label="Mô hình đang dùng"
                value={models[0]?.status === "ACTIVE" ? "Đang hoạt động" : "—"}
              />
            </section>
            <Panel title="Dữ liệu đi qua hệ thống như thế nào?">
              <div className="flow">
                CSV <b>→</b> Kho dữ liệu MinIO <b>→</b> Spark xử lý <b>→</b>{" "}
                DL4J dự báo <b>→</b> PostgreSQL lưu kết quả <b>→</b> Dashboard
                hiển thị
              </div>
            </Panel>
          </>
        )}
        {tab === "Data Lake" && (
          <>
            <section className="cards">
              <Card label="Bronze" value="91.300" />
              <Card label="Silver" value="91.300" />
              <Card label="Gold" value="91.300" />
            </section>
            <Panel title="Pipeline gần nhất">
              <Table rows={pipes} />
            </Panel>
          </>
        )}
        {tab === "Nhập dữ liệu" && (
          <Panel title="Tải dữ liệu bán hàng mới">
            <div className="upload-layout">
              <section className="upload-box">
                <label>
                  File CSV bán hàng
                  <input type="file" accept=".csv,text/csv" onChange={(event) => setUploadFile(event.target.files?.[0] || null)} />
                </label>
                <p>File phải có đúng bốn cột: <code>date, store, item, sales</code>. Phiên bản hiện tại nhận Cửa hàng 1 và mã sản phẩm 1–50; hệ thống kiểm tra ngày, giá trị âm và bản ghi trùng.</p>
                <button className="primary" disabled={!uploadFile || uploading} onClick={upload}>
                  {uploading ? "Đang tải lên…" : "Tải lên và xử lý"}
                </button>
              </section>
              <IngestionProgress run={uploadRun} onRetry={retryUpload} />
            </div>
            <h3 className="recent-title">Các lần xử lý gần đây</h3>
            <Table rows={pipes} />
          </Panel>
        )}
        {tab === "Phân tích" && (
          <Panel title="Phân tích xu hướng và nhu cầu bán hàng năm 2017">
            <div className="help">
              Phân tích sử dụng số lượng đã bán thực tế của Cửa hàng 1. Sản phẩm
              chỉ có mã ẩn danh vì dữ liệu nguồn không cung cấp tên hàng hóa.
            </div>
            <SalesAnalysis
              item={item}
              setItem={setItem}
              items={items}
              rows={sales}
              period={period}
              setPeriod={setPeriod}
              monthlyDemand={monthlyDemand}
              rankingMonth={rankingMonth}
              setRankingMonth={setRankingMonth}
            />
          </Panel>
        )}
        {tab === "Dự báo" && (
          <Panel title="Dự báo số sản phẩm bán trong 7 ngày">
            <div className="form">
              <Picker item={item} setItem={setItem} items={items} />
              <label>
                Ngày cuối cùng đã có doanh số
                <input
                  type="date"
                  value={origin}
                  onChange={(e) => setOrigin(e.target.value)}
                />
              </label>
              <button className="primary" disabled={busy} onClick={run}>
                {busy ? "Đang tính…" : "Xem dự báo 7 ngày"}
              </button>
            </div>
            {forecast && (
              <>
                <ForecastSummary
                  itemId={forecast.itemId}
                  rows={forecast.forecasts}
                />
                <ForecastTable rows={forecast.forecasts} />
              </>
            )}
          </Panel>
        )}
        {tab === "Đánh giá" && (
          <>
            <section className="cards">
              <Card label="Checkpoint" value="Seed 123" />
              <Card label="Horizon" value="7 ngày" />
              <Card label="CPU/GPU parity" value="PASS" />
            </section>
            <Panel title="Model đang phục vụ">
              <Table rows={models} />
            </Panel>
          </>
        )}
        {tab === "Spark Benchmark" && (
          <Panel title="1 Worker so với 2 Workers">
            {bench.length ? (
              <Table rows={bench} />
            ) : (
              <div className="empty">
                Chưa có số đo benchmark. Không hiển thị dữ liệu giả.
              </div>
            )}
          </Panel>
        )}
      </main>
    </div>
  );
}
const ingestionLabels: Record<string, string> = {
  ACCEPTED: "Đã tiếp nhận",
  RUNNING: "Đang xử lý bằng Spark",
  COMPLETED: "Hoàn thành",
  FAILED: "Thất bại",
};
const ingestionStageLabels: Record<string, string> = {
  UPLOAD: "Tải file lên",
  VALIDATE: "Kiểm tra dữ liệu",
  STORE_BRONZE: "Lưu bản gốc vào MinIO",
  SPARK_READ: "Spark đọc và kiểm tra dữ liệu",
  WRITE_SILVER_GOLD: "Tạo dữ liệu Silver và Gold",
  PUBLISH_POSTGRESQL: "Cập nhật PostgreSQL",
  REFRESH_DASHBOARD: "Làm mới Dashboard",
};
const ingestionStageOrder = Object.keys(ingestionStageLabels);
const formatDuration = (milliseconds: number) => {
  const seconds = Math.max(0, Math.floor(milliseconds / 1000));
  const minutes = Math.floor(seconds / 60);
  return minutes ? `${minutes} phút ${seconds % 60} giây` : `${seconds} giây`;
};
const IngestionProgress = ({ run, onRetry }: { run: Json | null; onRetry: () => void }) => {
  if (!run) return <section className="upload-status empty">Chưa có file nào được tải lên trong phiên này.</section>;
  const stageRows: Json[] = Array.isArray(run.stages) ? run.stages : [];
  const byName = new Map(stageRows.map((stage) => [stage.stage, stage]));
  const completed = ingestionStageOrder.filter((name) => byName.get(name)?.status === "COMPLETED").length;
  const current = byName.get(run.currentStage);
  const partial = current?.status === "RUNNING" && Number(current.totalRows) > 0
    ? Math.min(1, Number(current.completedRows) / Number(current.totalRows)) : 0;
  const percent = run.status === "COMPLETED" ? 100 : Math.round(((completed + partial) / ingestionStageOrder.length) * 100);
  const elapsedMs = Number(run.durationMillis || (Date.now() - new Date(run.startedAt).getTime()));
  const processed = Number(run.goldRows || run.processedRows || 0);
  const speed = elapsedMs > 0 ? processed / (elapsedMs / 1000) : 0;
  return (
    <section className={`upload-status status-${String(run.status).toLowerCase()}`}>
      <span>Trạng thái file</span>
      <strong>{ingestionLabels[run.status] || run.status}</strong>
      <small>{run.fileName} · {Number(run.recordCount || 0).toLocaleString("vi-VN")} bản ghi</small>
      {run.duplicate && <p>{run.message}</p>}
      <div className="progress-summary">
        <div><span>Tiến độ tổng</span><strong>{percent}%</strong></div>
        <div><span>Thời gian đã chạy</span><strong>{formatDuration(elapsedMs)}</strong></div>
        <div><span>Tốc độ xử lý</span><strong>{speed > 0 ? `${Math.round(speed).toLocaleString("vi-VN")} bản ghi/giây` : "Đang đo"}</strong></div>
      </div>
      <div className="progress-meter" role="progressbar" aria-valuemin={0} aria-valuemax={100} aria-valuenow={percent}>
        <span style={{ width: `${percent}%` }} />
      </div>
      <p className="row-progress">Đã xử lý: <b>{processed.toLocaleString("vi-VN")}</b> / {Number(run.recordCount || 0).toLocaleString("vi-VN")} bản ghi</p>
      <ol className="progress-timeline">
        {ingestionStageOrder.map((name) => {
          const stage = byName.get(name);
          const status = stage?.status || (name === run.currentStage ? "RUNNING" : "WAITING");
          return <li key={name} className={`stage-${String(status).toLowerCase()}`}>
            <i aria-hidden="true" /><div><b>{ingestionStageLabels[name]}</b><small>{status === "COMPLETED" ? "Hoàn thành" : status === "RUNNING" ? "Đang thực hiện" : status === "FAILED" ? "Thất bại" : "Đang chờ"}</small></div>
          </li>;
        })}
      </ol>
      {run.status === "FAILED" && <><p className="failure-detail">{run.error || "Spark xử lý thất bại."}</p><button onClick={onRetry}>Thử xử lý lại</button></>}
      {run.status === "COMPLETED" && <p>Dữ liệu mới đã được cập nhật vào PostgreSQL và Dashboard.</p>}
    </section>
  );
};
const Card = ({ label, value }: { label: string; value: string }) => (
  <article className="card">
    <span>{label}</span>
    <strong>{value}</strong>
  </article>
);
const Panel = ({
  title,
  children,
}: {
  title: string;
  children: React.ReactNode;
}) => (
  <section className="panel">
    <h2>{title}</h2>
    {children}
  </section>
);
const Picker = ({
  item,
  setItem,
  items,
}: {
  item: number;
  setItem: (n: number) => void;
  items: Json[];
}) => (
  <label>
    Mã sản phẩm <small>Dữ liệu gốc không có tên hàng hóa</small>
    <select value={item} onChange={(e) => setItem(+e.target.value)}>
      {(items.length
        ? items
        : Array.from({ length: 50 }, (_, i) => ({ id: i + 1 }))
      ).map((x) => (
        <option value={x.id}>Sản phẩm #{x.id} (mã ẩn danh)</option>
      ))}
    </select>
  </label>
);
const dateOptions: Intl.DateTimeFormatOptions = {
  weekday: "long",
  day: "2-digit",
  month: "2-digit",
  year: "numeric",
};
const formatDate = (value: string) =>
  new Date(`${value}T00:00:00`).toLocaleDateString("vi-VN", dateOptions);
const formatUnits = (value: any) => `${Math.round(Number(value))} sản phẩm`;
const ForecastSummary = ({
  itemId,
  rows,
}: {
  itemId: number;
  rows: Json[];
}) => {
  const total = rows.reduce((sum, row) => sum + Number(row.prediction || 0), 0);
  const previousTotal = rows.reduce(
    (sum, row) => sum + Number(row.weeklyNaive || 0),
    0,
  );
  const difference = total - previousTotal;
  const percentage = previousTotal > 0 ? (difference / previousTotal) * 100 : null;
  const demandDirection =
    difference > 0
      ? "Nhu cầu tăng"
      : difference < 0
        ? "Nhu cầu giảm"
        : "Nhu cầu không đổi";
  return (
    <section className="forecast-total">
      <span>Sản phẩm #{itemId}</span>
      <div>
        <small>Tổng nhu cầu dự báo trong 7 ngày</small>
        <strong>{formatUnits(total)}</strong>
      </div>
      <div className="previous-total">
        <small>Tổng cùng kỳ tuần trước</small>
        <strong>{formatUnits(previousTotal)}</strong>
      </div>
      <div className={difference >= 0 ? "demand-up" : "demand-down"}>
        <small>Chênh lệch nhu cầu</small>
        <strong>
          {demandDirection} {percentage == null ? "—" : `${Math.abs(percentage).toFixed(1)}%`}
        </strong>
        <span>
          {difference >= 0 ? "+" : "−"}
          {formatUnits(Math.abs(difference))}
        </span>
        {percentage == null && <em>Không tính được phần trăm vì cùng kỳ trước bằng 0.</em>}
      </div>
      <p>
        {formatDate(rows[0].targetDate)} –{" "}
        {formatDate(rows[rows.length - 1].targetDate)}
      </p>
    </section>
  );
};
const ForecastTable = ({ rows }: { rows: Json[] }) => (
  <div className="forecast-grid">
    {rows.map((r) => (
      <article key={r.horizon}>
        <h3>{formatDate(r.targetDate)}</h3>
        <dl>
          <div className="forecast-main">
            <dt>Sản phẩm dự kiến bán</dt>
            <dd>{formatUnits(r.prediction)}</dd>
          </div>
          <div>
            <dt>Số sản phẩm bán cùng ngày tuần trước</dt>
            <dd>{formatUnits(r.weeklyNaive)}</dd>
          </div>
          <div>
            <dt>Số sản phẩm thực tế đã bán</dt>
            <dd>
              {r.actual == null ? "Chưa có dữ liệu" : formatUnits(r.actual)}
            </dd>
          </div>
        </dl>
      </article>
    ))}
  </div>
);
const Table = ({ rows }: { rows: Json[] }) =>
  rows.length ? (
    <div className="table">
      <table>
        <thead>
          <tr>
            {Object.keys(rows[0]).map((k) => (
              <th>{k}</th>
            ))}
          </tr>
        </thead>
        <tbody>
          {rows.slice(0, 20).map((r, i) => (
            <tr key={i}>
              {Object.values(r).map((v, j) => (
                <td key={j}>{v === null ? "—" : String(v)}</td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  ) : (
    <div className="empty">Chưa có dữ liệu thực tế.</div>
  );
const localIso = (date: Date) =>
  `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, "0")}-${String(date.getDate()).padStart(2, "0")}`;
const aggregateSales = (rows: Json[], period: "day" | "week" | "month"): Json[] => {
  if (period === "day")
    return rows.map((row) => ({
      ...row,
      label: new Date(`${row.date}T00:00:00`).toLocaleDateString("vi-VN"),
      shortLabel: shortDate(row.date),
    }));
  const grouped = new Map<string, number>();
  rows.forEach((row) => {
    const date = new Date(`${row.date}T00:00:00`);
    let key;
    if (period === "month") key = row.date.slice(0, 7) + "-01";
    else {
      const offset = (date.getDay() + 6) % 7;
      date.setDate(date.getDate() - offset);
      key = localIso(date);
    }
    grouped.set(key, (grouped.get(key) || 0) + Number(row.sales));
  });
  return Array.from(grouped, ([date, sales]) => {
    if (period === "month") {
      const d = new Date(`${date}T00:00:00`);
      return {
        date,
        sales,
        label: `Tháng ${d.getMonth() + 1}/${d.getFullYear()}`,
        shortLabel: `T${d.getMonth() + 1}`,
      };
    }
    const start = new Date(`${date}T00:00:00`),
      end = new Date(start);
    end.setDate(end.getDate() + 6);
    return {
      date,
      sales,
      label: `Tuần ${shortDate(date)} – ${shortDate(localIso(end))}`,
      shortLabel: shortDate(date),
    };
  });
};
const SalesAnalysis = ({
  item,
  setItem,
  items,
  rows,
  period,
  setPeriod,
  monthlyDemand,
  rankingMonth,
  setRankingMonth,
}: {
  item: number;
  setItem: (n: number) => void;
  items: Json[];
  rows: Json[];
  period: "day" | "week" | "month";
  setPeriod: (value: "day" | "week" | "month") => void;
  monthlyDemand: Json[];
  rankingMonth: string;
  setRankingMonth: (value: string) => void;
}) => {
  const aggregated = aggregateSales(rows, period);
  const values = aggregated.map((row) => Number(row.sales));
  const total = values.reduce((sum, value) => sum + value, 0);
  const average = values.length ? total / values.length : 0;
  const highest = aggregated.reduce(
    (best, row) =>
      !best || Number(row.sales) > Number(best.sales) ? row : best,
    null as Json | null,
  );
  const previous = values.length > 1 ? values[values.length - 2] : 0,
    current = values.length ? values[values.length - 1] : 0;
  const change = previous ? ((current - previous) / previous) * 100 : null;
  const ranking = monthlyDemand.filter((row) => row.month === rankingMonth);
  const periodName =
    period === "day" ? "ngày" : period === "week" ? "tuần" : "tháng";
  return (
    <>
      <div className="analysis-controls">
        <Picker item={item} setItem={setItem} items={items} />
        <div className="period-switch" aria-label="Chọn mức tổng hợp">
          <button
            className={period === "day" ? "selected" : ""}
            onClick={() => setPeriod("day")}
          >
            Theo ngày
          </button>
          <button
            className={period === "week" ? "selected" : ""}
            onClick={() => setPeriod("week")}
          >
            Theo tuần
          </button>
          <button
            className={period === "month" ? "selected" : ""}
            onClick={() => setPeriod("month")}
          >
            Theo tháng
          </button>
        </div>
      </div>
      {aggregated.length ? (
        <>
          <section className="insight-cards">
            <div>
              <span>Tổng nhu cầu</span>
              <strong>
                {Math.round(total).toLocaleString("vi-VN")} sản phẩm
              </strong>
            </div>
            <div>
              <span>Trung bình mỗi {periodName}</span>
              <strong>{average.toFixed(1)} sản phẩm</strong>
            </div>
            <div>
              <span>
                {periodName[0].toUpperCase() + periodName.slice(1)} có nhu cầu
                cao nhất
              </span>
              <strong>{highest?.label}</strong>
              <small>
                {Math.round(Number(highest?.sales)).toLocaleString("vi-VN")} sản
                phẩm
              </small>
            </div>
            <div>
              <span>So với kỳ trước</span>
              <strong>
                {change == null
                  ? "Chưa đủ dữ liệu"
                  : `${change >= 0 ? "+" : ""}${change.toFixed(1)}%`}
              </strong>
              <small>
                {change == null
                  ? ""
                  : change >= 0
                    ? "Nhu cầu đang tăng"
                    : "Nhu cầu đang giảm"}
              </small>
            </div>
          </section>
          <Chart rows={aggregated} period={period} />
        </>
      ) : (
        <div className="empty">Chưa có dữ liệu.</div>
      )}
      <section className="ranking-panel">
        <div className="ranking-title">
          <div>
            <h3>Sản phẩm bán chạy theo tháng</h3>
            <p>
              So sánh tổng số lượng bán của 50 mã sản phẩm trong Cửa hàng 1.
            </p>
          </div>
          <label>
            Tháng
            <select
              value={rankingMonth}
              onChange={(event) => setRankingMonth(event.target.value)}
            >
              {Array.from({ length: 12 }, (_, index) => {
                const value = `2017-${String(index + 1).padStart(2, "0")}`;
                return (
                  <option key={value} value={value}>
                    Tháng {index + 1}/2017
                  </option>
                );
              })}
            </select>
          </label>
        </div>
        {ranking.length ? (
          <div className="ranking-list">
            {ranking.map((row) => (
              <article key={row.itemId}>
                <b>#{row.rank}</b>
                <span>Sản phẩm #{row.itemId}</span>
                <strong>
                  {Number(row.totalSales).toLocaleString("vi-VN")} sản phẩm
                </strong>
                <small>
                  TB {Number(row.averageDailySales).toFixed(1)}/ngày · Cao nhất{" "}
                  {Number(row.peakDailySales)}/ngày
                </small>
              </article>
            ))}
          </div>
        ) : (
          <div className="empty">Chưa có dữ liệu xếp hạng tháng này.</div>
        )}
      </section>
    </>
  );
};
const shortDate = (value: string) =>
  new Date(`${value}T00:00:00`).toLocaleDateString("vi-VN", {
    day: "2-digit",
    month: "2-digit",
  });
const formatChartPoint = (row: Json) =>
  `${row.label || new Date(`${row.date}T00:00:00`).toLocaleDateString("vi-VN")}: ${Math.round(Number(row.sales))} sản phẩm`;
const Chart = ({
  rows,
  period,
}: {
  rows: Json[];
  period: "day" | "week" | "month";
}) => {
  if (!rows.length) return <div className="empty">Chưa có dữ liệu.</div>;
  const vals = rows.map((x) => Number(x.sales));
  const max = Math.max(...vals);
  const width = 960,
    height = 340,
    left = 68,
    right = 24,
    top = 24,
    bottom = 58,
    plotWidth = width - left - right,
    plotHeight = height - top - bottom,
    axisMax = Math.max(10, Math.ceil(max / 10) * 10);
  const x = (index: number) =>
    left + (rows.length === 1 ? 0 : (index / (rows.length - 1)) * plotWidth);
  const y = (value: number) =>
    top + plotHeight - (value / axisMax) * plotHeight;
  const points = vals
    .map((value, index) => `${x(index)},${y(value)}`)
    .join(" ");
  const yTicks = Array.from({ length: 6 }, (_, index) =>
    Math.round((axisMax * (5 - index)) / 5),
  );
  const xIndexes = Array.from(
    new Set([
      0,
      Math.round((rows.length - 1) / 4),
      Math.round((rows.length - 1) / 2),
      Math.round(((rows.length - 1) * 3) / 4),
      rows.length - 1,
    ]),
  );
  const periodLabel =
    period === "day" ? "ngày" : period === "week" ? "tuần" : "tháng";
  return (
    <div className="analysis-chart">
      <div className="chart-scroll">
        <svg
          viewBox={`0 0 ${width} ${height}`}
          className="chart"
          role="img"
          aria-label={`Biểu đồ số sản phẩm đã bán theo ${periodLabel}`}
        >
          <text
            className="chart-axis-title y-title"
            x="18"
            y={top + plotHeight / 2}
            transform={`rotate(-90 18 ${top + plotHeight / 2})`}
          >
            Số sản phẩm đã bán
          </text>
          {yTicks.map((value) => (
            <g key={value} className="chart-grid">
              <line x1={left} x2={width - right} y1={y(value)} y2={y(value)} />
              <text x={left - 10} y={y(value) + 4}>
                {value}
              </text>
            </g>
          ))}
          <line
            className="chart-axis"
            x1={left}
            x2={left}
            y1={top}
            y2={top + plotHeight}
          />
          <line
            className="chart-axis"
            x1={left}
            x2={width - right}
            y1={top + plotHeight}
            y2={top + plotHeight}
          />
          {xIndexes.map((index) => (
            <g key={index}>
              <line
                className="chart-tick"
                x1={x(index)}
                x2={x(index)}
                y1={top + plotHeight}
                y2={top + plotHeight + 6}
              />
              <text
                className="chart-date"
                x={x(index)}
                y={top + plotHeight + 23}
              >
                {rows[index].shortLabel || shortDate(rows[index].date)}
              </text>
            </g>
          ))}
          <text
            className="chart-axis-title"
            x={left + plotWidth / 2}
            y={height - 5}
          >
            Kỳ bán hàng ({periodLabel})
          </text>
          <polyline className="chart-line" points={points} />
          {rows.map((row, index) => (
            <circle
              className="chart-point"
              key={row.date}
              cx={x(index)}
              cy={y(Number(row.sales))}
              r="3"
            >
              <title>{formatChartPoint(row)}</title>
            </circle>
          ))}
        </svg>
      </div>
      <p className="chart-hint">
        Rê chuột vào một điểm để xem kỳ và tổng số sản phẩm đã bán.
      </p>
    </div>
  );
};
createRoot(document.getElementById("root")!).render(<App />);
