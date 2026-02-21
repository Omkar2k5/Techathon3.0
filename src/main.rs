mod udp;
mod ip;
mod tcp;
mod llm;
mod conversation;
mod persistence;
mod lab_module;

use std::collections::HashSet;
use std::sync::Arc;
use actix_web::{get, App, HttpResponse, HttpServer, Responder, web};
use actix_cors::Cors;
use rust_embed::Embed;
use tokio::sync::Mutex;
use udp::{periodic_broadcast, receive_broadcast};
use tcp::{connect_to_peers, listen_for_connections};
use conversation::CONVERSATION_STORE;

#[derive(Embed)]
#[folder = "./webpage/build/"]
struct WebAssets;

/// Serve a file from the embedded build, falling back to index.html for SPA routing
fn send_asset(path: &str) -> HttpResponse {
    // Try the exact path first
    if let Some(file) = WebAssets::get(path) {
        let mime = mime_guess::from_path(path).first_or_octet_stream();
        return HttpResponse::Ok()
            .content_type(mime.to_string())
            .body(file.data);
    }
    // SPA fallback: serve index.html for all unknown paths (React Router handles it)
    if let Some(index) = WebAssets::get("index.html") {
        return HttpResponse::Ok()
            .content_type("text/html")
            .body(index.data);
    }
    HttpResponse::NotFound().body("Not found")
}

#[get("/")]
async fn serve_root() -> impl Responder {
    send_asset("index.html")
}

/// Serve static assets (JS, CSS, images) from /assets/
#[get("/assets/{file:.*}")]
async fn serve_assets(file: web::Path<String>) -> impl Responder {
    send_asset(&format!("assets/{}", file.into_inner()))
}

/// SPA catch-all: any path that isn't /api/* or /assets/* gets index.html
#[get("/{path:.*}")]
async fn serve_spa(_path: web::Path<String>) -> impl Responder {
    send_asset("index.html")
}

#[get("/peers")]
async fn get_peers() -> Result<HttpResponse, actix_web::Error> {
    println!("API: Received request for peer conversations");
    let peer_conversations = CONVERSATION_STORE.get_peer_conversations().await;
    println!("API: Found {} peer conversations", peer_conversations.len());
    Ok(HttpResponse::Ok().json(peer_conversations))
}

#[actix_web::main]
async fn main() -> std::io::Result<()> {
    // Initialize conversations directory
    if let Err(e) = persistence::init_conversations_dir().await {
        eprintln!("Error initializing conversations directory: {}", e);
        return Err(e);
    }

    // Load saved conversations
    if let Err(e) = CONVERSATION_STORE.load_saved_conversations().await {
        eprintln!("Error loading saved conversations: {}", e);
    }

    // Initialize MongoDB for lab module
    if let Err(e) = lab_module::mongo_connection::init_db().await {
        eprintln!("LAB: MongoDB init failed: {} (lab features disabled)", e);
    }

    let received_ips = Arc::new(Mutex::new(HashSet::new()));
    let received_ips_clone = received_ips.clone();

    tokio::spawn(async move {
        if let Err(e) = receive_broadcast(received_ips_clone).await {
            eprintln!("Error in UDP receiver task: {}", e);
        }
    });
    tokio::spawn(listen_for_connections());
    tokio::spawn(periodic_broadcast());
    let received_ips_clone = received_ips.clone();
    tokio::spawn(connect_to_peers(received_ips_clone));
    tokio::spawn(lab_module::scheduler::run_scheduler());

    // Auto-open the browser at the app root
    let _ = open::that("http://localhost:8080");

    HttpServer::new(|| {
        App::new()
            .wrap(
                Cors::default()
                    .allow_any_origin()
                    .allow_any_method()
                    .allow_any_header()
                    .expose_headers(["content-type", "content-length"])
                    .max_age(3600),
            )
            // API routes (must come first)
            .service(
                web::scope("/api")
                    .service(llm::chat)
                    .configure(lab_module::config),
            )
            .service(get_peers)
            // Static assets
            .service(serve_assets)
            // SPA root + catch-all (must come last)
            .service(serve_root)
            .service(serve_spa)
    })
    .bind(("0.0.0.0", 8080))?
    .run()
    .await
}
