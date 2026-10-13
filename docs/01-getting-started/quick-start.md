# Quick start

Từ root repository:

```powershell
.\project.ps1 setup
.\project.ps1 up
.\project.ps1 status
```

`setup` kiểm tra Java, Maven, Docker, tạo `.env` từ `.env.example` nếu thiếu và xác minh dataset cùng
deployment model. Đổi hai mật khẩu mẫu trong `.env` trước khi dùng máy chung.

Mở <http://localhost:3000>. Dừng stack nhưng giữ named volumes bằng:

```powershell
.\project.ps1 down
```

Không dùng `docker compose down -v` nếu chưa chủ động xóa MinIO và PostgreSQL.
