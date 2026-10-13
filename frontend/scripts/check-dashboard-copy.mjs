import { readFileSync } from "node:fs";

const source = readFileSync(
  new URL("../src/main.tsx", import.meta.url),
  "utf8",
);
const required = [
  "Tổng nhu cầu dự báo trong 7 ngày",
  "Sản phẩm dự kiến bán",
  "Số sản phẩm bán cùng ngày tuần trước",
  "Số sản phẩm thực tế đã bán",
  'toLocaleDateString("vi-VN", dateOptions)',
  "forecast-total",
  "Tổng cùng kỳ tuần trước",
  "Chênh lệch nhu cầu",
  "Nhu cầu tăng",
  "Nhu cầu giảm",
  "previousTotal > 0",
  "Số sản phẩm đã bán",
  "Trung bình mỗi {periodName}",
  "cao nhất",
  "chart-axis",
  "chart-grid",
  "<title>{formatChartPoint",
  "Theo ngày",
  "Theo tuần",
  "Theo tháng",
  "Tổng nhu cầu",
  "So với kỳ trước",
  "Sản phẩm bán chạy theo tháng",
  "aggregateSales",
  "/api/analytics/monthly-demand",
];
const forbidden = [
  "Bước 1",
  "Bước 2",
  "Bước 3",
  "Ví dụ:",
  "Ngày dự báo {r.horizon}",
];
const missing = required.filter((text) => !source.includes(text));
const retained = forbidden.filter((text) => source.includes(text));

if (missing.length || retained.length) {
  if (missing.length)
    console.error(`Thiếu nội dung kết quả: ${missing.join(", ")}`);
  if (retained.length)
    console.error(`Còn nội dung phải bỏ: ${retained.join(", ")}`);
  process.exit(1);
}

console.log("DASHBOARD_RESULT_COPY_OK");
