// LLM module for language model related functionality
use actix_web::{post, web, HttpResponse, Error};
use serde::{Deserialize, Serialize};
use reqwest::Client;
use chrono::Utc;
use crate::conversation::{ChatMessage, CONVERSATION_STORE, HostInfo, MessageType};
use crate::tcp::LLM_CONNECTIONS;
use crate::udp::LLM_UDP_PEERS;
use std::time::Duration;
use hostname;

// Remove the constant and make it a function that returns the correct URL
async fn get_ollama_url() -> String {
    let connections = LLM_CONNECTIONS.lock().await;
    if let Some((host, port)) = connections.values().next() {
        format!("http://{}:{}", host, port)
    } else {
        "http://127.0.0.1:11434".to_string()
    }
}

const REMOTE_REQUEST_TIMEOUT: Duration = Duration::from_secs(120);

#[derive(Deserialize)]
pub struct ChatRequest {
    message: String,
    sender: String,
}

#[derive(Serialize, Deserialize, Debug, Clone)]
pub struct OllamaMessage {
    pub role: String,
    pub content: String,
}

#[derive(Serialize, Deserialize, Clone)]
pub struct OllamaRequest {
    pub model: String,
    pub messages: Vec<OllamaMessage>,
    pub stream: bool,   // always false — get single JSON, not NDJSON
}

#[derive(Serialize, Deserialize, Debug)]
struct OllamaResponse {
    model: String,
    created_at: String,
    message: OllamaMessage,
    done: bool,
    #[serde(skip_serializing_if = "Option::is_none")]
    done_reason: Option<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    context: Option<Vec<i32>>,
    #[serde(skip_serializing_if = "Option::is_none")]
    total_duration: Option<i64>,
    #[serde(skip_serializing_if = "Option::is_none")]
    load_duration: Option<i64>,
    #[serde(skip_serializing_if = "Option::is_none")]
    prompt_eval_count: Option<i32>,
    #[serde(skip_serializing_if = "Option::is_none")]
    eval_count: Option<i32>,
    #[serde(skip_serializing_if = "Option::is_none")]
    eval_duration: Option<i64>,
}

// Update the is_local_ollama_available function
async fn is_local_ollama_available() -> bool {
    if let Ok(client) = Client::builder()
        .timeout(Duration::from_secs(2))
        .build() 
    {
        let url = get_ollama_url().await;
        match client.get(&url).send().await {
            Ok(response) => response.status().is_success(),
            Err(_) => false,
        }
    } else {
        false
    }
}

pub async fn try_local_llm(req: &OllamaRequest) -> Result<String, String> {
    // Always connect to local Ollama directly — never use LAN peer URL here
    let client = Client::builder()
        .timeout(Duration::from_secs(120))
        .build()
        .map_err(|e| format!("HTTP client error: {}", e))?;

    let response = client
        .post("http://127.0.0.1:11434/api/chat")
        .json(&req)
        .send()
        .await
        .map_err(|e| format!("Failed to connect to local Ollama: {}", e))?;

    if !response.status().is_success() {
        return Err(format!("Local Ollama error: {}", response.status()));
    }

    let body = response.text().await
        .map_err(|e| format!("Failed to read Ollama response: {}", e))?;

    process_ollama_response(&body)
}

pub async fn try_remote_llm(req: &OllamaRequest) -> Result<String, String> {
    // Collect peer IPs from both sources:
    // 1. LLM_CONNECTIONS  — TCP-handshaked peers (usually empty when port 7878 is firewalled)
    // 2. LLM_UDP_PEERS    — UDP-discovered peers with has_llm=true (works without TCP)
    let mut peer_hosts: Vec<String> = {
        let connections = LLM_CONNECTIONS.lock().await;
        connections.values().map(|(host, _)| host.clone()).collect()
    };
    {
        let udp_peers = LLM_UDP_PEERS.lock().await;
        for ip in udp_peers.iter() {
            if !peer_hosts.contains(ip) {
                peer_hosts.push(ip.clone());
            }
        }
    }

    if peer_hosts.is_empty() {
        return Err("No LLM peers discovered yet — waiting for UDP broadcast".to_string());
    }

    let client = Client::builder()
        .timeout(REMOTE_REQUEST_TIMEOUT)
        .connect_timeout(Duration::from_secs(10))
        .build()
        .map_err(|e| format!("HTTP client error: {}", e))?;

    for host in peer_hosts {
        let proxy_url = format!("http://{}:8080/api/llm/proxy", host);
        println!("Attempting remote LLM via EduNet proxy at {}", proxy_url);

        match client.post(&proxy_url).json(req).send().await {
            Ok(response) if response.status().is_success() => {
                let body = response.text().await
                    .map_err(|e| format!("Failed to read proxy response: {}", e))?;
                match process_ollama_response(&body) {
                    Ok(result) => {
                        println!("Remote LLM via {} succeeded", host);
                        return Ok(result);
                    }
                    Err(e) => println!("Proxy parse error from {}: {}", host, e),
                }
            }
            Ok(response) => println!("Proxy at {} returned {}", proxy_url, response.status()),
            Err(e) => println!("Failed to reach proxy at {}: {}", proxy_url, e),
        }
    }

    Err("No LLM peer proxy responded — check that the LLM host's EduNet is running".to_string())
}

/// LLM proxy endpoint — peers call this to use our local Ollama via port 8080
#[post("/llm/proxy")]
pub async fn llm_proxy(body: web::Json<OllamaRequest>) -> HttpResponse {
    let req = body.into_inner();
    match try_local_llm(&req).await {
        Ok(reply) => HttpResponse::Ok().json(serde_json::json!({ 
            "model": &req.model,
            "created_at": chrono::Utc::now().to_rfc3339(),
            "message": { "role": "assistant", "content": reply },
            "done": true
        })),
        Err(e) => HttpResponse::ServiceUnavailable()
            .json(serde_json::json!({ "error": e })),
    }
}

fn process_ollama_response(body: &str) -> Result<String, String> {
    let mut full_response = String::new();
    let mut response_complete = false;

    for line in body.lines() {
        if let Ok(resp) = serde_json::from_str::<OllamaResponse>(line) {
            full_response.push_str(&resp.message.content);
            if resp.done {
                response_complete = true;
            }
        }
    }

    if !response_complete {
        return Err("Incomplete response from LLM".to_string());
    }

    if full_response.trim().is_empty() {
        return Err("Empty response from LLM".to_string());
    }

    Ok(full_response)
}

#[post("/chat")]
pub async fn chat(req: web::Json<ChatRequest>) -> Result<HttpResponse, Error> {
    let hostname = hostname::get()
        .map(|h| h.to_string_lossy().to_string())
        .unwrap_or_else(|_| "Unknown".to_string());
    
    let ip_address = std::net::TcpStream::connect("8.8.8.8:53")
        .and_then(|s| s.local_addr())
        .map(|addr| addr.ip().to_string())
        .unwrap_or_else(|_| "Unknown".to_string());

    let host_info = HostInfo {
        hostname: hostname.clone(),
        ip_address: ip_address.clone(),
        is_llm_host: is_local_ollama_available().await,
    };

    // Create user question message
    let question_message = ChatMessage {
        content: req.message.clone(),
        timestamp: Utc::now(),
        sender: req.sender.clone(),
        message_type: MessageType::Question,
        host_info: host_info.clone(),
    };

    // Save the question
    CONVERSATION_STORE.add_message("local".to_string(), question_message).await;

    let ollama_req = OllamaRequest {
        model: "phi3-fast:latest".to_string(),
        stream: false,
        messages: vec![
            OllamaMessage {
                role: "user".to_string(),
                content: req.message.clone(),
            }
        ],
    };

    // Check if we have local Ollama first
    let has_local_llm = is_local_ollama_available().await;
    
    let response = if has_local_llm {
        // Try local first if available
        match try_local_llm(&ollama_req).await {
            Ok(response) => response,
            Err(local_error) => {
                // If local fails, try remote
                match try_remote_llm(&ollama_req).await {
                    Ok(response) => response,
                    Err(remote_error) => {
                        return Ok(HttpResponse::ServiceUnavailable()
                            .json(serde_json::json!({
                                "error": "No available LLM service",
                                "details": format!("Local error: {}. Remote error: {}", local_error, remote_error)
                            })));
                    }
                }
            }
        }
    } else {
        // No local LLM, try remote directly
        match try_remote_llm(&ollama_req).await {
            Ok(response) => response,
            Err(remote_error) => {
                return Ok(HttpResponse::ServiceUnavailable()
                    .json(serde_json::json!({
                        "error": "No available LLM service",
                        "details": format!("No local LLM available. Remote error: {}", remote_error)
                    })));
            }
        }
    };

    // Create response message with host info
    let response_message = ChatMessage {
        content: response.clone(),
        timestamp: Utc::now(),
        sender: "LLM".to_string(),
        message_type: MessageType::Response,
        host_info,
    };

    // Save the response
    CONVERSATION_STORE.add_message("local".to_string(), response_message.clone()).await;

    Ok(HttpResponse::Ok().json(response_message))
}