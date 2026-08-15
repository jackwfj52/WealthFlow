// release 构建不显示控制台窗口
#![cfg_attr(not(debug_assertions), windows_subsystem = "windows")]

use std::net::TcpStream;
use std::process::{Child, Command, Stdio};
use std::sync::Mutex;
use std::time::{Duration, Instant};
use tauri::{Manager, RunEvent, WebviewUrl, WebviewWindowBuilder};

/// 桌面端内嵌后端固定端口（前端生产构建 .env.production 与其保持一致）
const SERVER_PORT: u16 = 18080;

struct ServerProcess(Mutex<Option<Child>>);

/// 启动内嵌后端：jpackage 镜像自带精简 JRE，直接以 javaw -jar 方式运行
fn spawn_backend(app: &tauri::AppHandle) -> Result<Child, Box<dyn std::error::Error>> {
    let resource_dir = app.path().resource_dir()?;
    let java = resource_dir
        .join("server")
        .join("runtime")
        .join("bin")
        .join("javaw.exe");
    let jar = resource_dir.join("server").join("app").join("wealthflow.jar");
    if !java.exists() || !jar.exists() {
        return Err(format!("内嵌后端缺失：{} / {}", java.display(), jar.display()).into());
    }

    // 用户数据放在 AppData 数据目录，升级安装不丢失
    let data_dir = app.path().app_data_dir()?;
    std::fs::create_dir_all(&data_dir)?;
    let db_path = data_dir.join("wealthflow.db").to_string_lossy().replace('\\', "/");

    let child = Command::new(java)
        .arg("-jar")
        .arg(jar)
        .env("SERVER_PORT", SERVER_PORT.to_string())
        .env("WEALTHFLOW_DB_PATH", db_path)
        .creation_flags(0x0800_0000) // CREATE_NO_WINDOW：不弹出控制台
        .stdin(Stdio::null())
        .spawn()?;
    Ok(child)
}

/// 轮询等待后端就绪：TCP 端口可连接即视为启动完成
fn wait_for_server(child: &mut Child, timeout: Duration) -> bool {
    let deadline = Instant::now() + timeout;
    while Instant::now() < deadline {
        if TcpStream::connect(("127.0.0.1", SERVER_PORT)).is_ok() {
            return true;
        }
        if let Ok(Some(status)) = child.try_wait() {
            eprintln!("[wealthflow] 后端进程提前退出：{status:?}");
            return false;
        }
        std::thread::sleep(Duration::from_millis(300));
    }
    false
}

fn main() {
    tauri::Builder::default()
        .setup(|app| {
            // release 模式拉起内嵌后端并等待就绪；debug 模式走 Vite 代理（后端自行启动于 8080）
            #[cfg(not(debug_assertions))]
            {
                match spawn_backend(app.handle()) {
                    Ok(mut child) => {
                        if wait_for_server(&mut child, Duration::from_secs(60)) {
                            app.manage(ServerProcess(Mutex::new(Some(child))));
                        } else {
                            eprintln!("[wealthflow] 等待后端启动超时");
                            let _ = child.kill();
                        }
                    }
                    Err(e) => eprintln!("[wealthflow] 启动内嵌后端失败：{e}"),
                }
            }

            WebviewWindowBuilder::new(app, "main", WebviewUrl::App("index.html".into()))
                .title("WealthFlow")
                .inner_size(1280.0, 800.0)
                .min_inner_size(960.0, 640.0)
                .build()?;
            Ok(())
        })
        .build(tauri::generate_context!())
        .expect("error while building tauri application")
        .run(|app, event| {
            if let RunEvent::Exit = event {
                if let Some(proc) = app.try_state::<ServerProcess>() {
                    if let Some(mut child) = proc.0.lock().unwrap().take() {
                        let _ = child.kill();
                    }
                }
            }
        });
}
