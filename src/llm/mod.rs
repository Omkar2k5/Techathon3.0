// LLM module for language model related functionality
use actix_web::{post, web, HttpResponse, Error};
use serde::{Deserialize, Serialize};
use reqwest::Client;
use chrono::Utc;
use crate::conversation::{ChatMessage, CONVERSATION_STORE, HostInfo, MessageType};
use crate::tcp::LLM_CONNECTIONS;
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
    let connections = LLM_CONNECTIONS.lock().await;
    
    if connections.is_empty() {
        return Err("No remote LLM connections available".to_string());
    }

    // Try each known LLM connection with retry logic
    for (peer, (host, port)) in connections.iter() {
        // Retry up to 3 times with exponential backoff
        for attempt in 1..=3 {
            let client = Client::builder()
                .timeout(REMOTE_REQUEST_TIMEOUT)
                .connect_timeout(Duration::from_secs(30))
                .pool_idle_timeout(Duration::from_secs(90))
                .pool_max_idle_per_host(2)
                .tcp_keepalive(Duration::from_secs(60))
                .build()
                .map_err(|e| format!("Failed to create HTTP client: {}", e))?;

            let remote_url = format!("http://{}:{}/api/chat", host, port);
            
            println!("Attempting to use remote LLM at {} (attempt {}/3)", remote_url, attempt);
            
            match client.post(&remote_url)
                .json(&req)
                .timeout(REMOTE_REQUEST_TIMEOUT)
                .send()
                .await {
                    Ok(response) => {
                        if response.status().is_success() {
                            let body = response.text().await
                                .map_err(|e| format!("Failed to get remote LLM response: {}", e))?;
                            
                            match process_ollama_response(&body) {
                                Ok(result) => {
                                    println!("Successfully used remote LLM from peer {}", peer);
                                    return Ok(result)
                                },
                                Err(e) => println!("Failed to process response from {}: {}", peer, e),
                            }
                        } else {
                            println!("Remote LLM {} returned error status: {}", peer, response.status());
                        }
                    },
                    Err(e) => {
                        println!("Failed to connect to remote LLM {} (attempt {}/3): {}", peer, attempt, e);
                        
                        // Exponential backoff: 2s, 5s, 10s
                        if attempt < 3 {
                            let delay = match attempt {
                                1 => Duration::from_secs(2),
                                2 => Duration::from_secs(5),
                                _ => Duration::from_secs(10),
                            };
                            println!("Retrying in {} seconds...", delay.as_secs());
                            tokio::time::sleep(delay).await;
                        }
                    },
                }
        }
    }
    
    Err("No available LLM connections responded successfully after retries".to_string())
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