use tokio::net::UdpSocket;
use tokio::time::{Duration, interval};
use std::collections::{HashSet, HashMap};
use std::str;
use tokio::sync::Mutex;
use std::sync::Arc;
use ipconfig::get_adapters;
use std::net::{IpAddr, Ipv4Addr};
use serde::{Serialize, Deserialize};
use reqwest::Client;
use chrono::{DateTime, Utc};
use crate::ip::is_my_ip;
use once_cell::sync::Lazy;

const BROADCAST_PORT: u16 = 5000;
const BROADCAST_INTERVAL: Duration = Duration::from_secs(30);
const LISTEN_ADDR: &str = "0.0.0.0:5000";
const OLLAMA_CHECK_URL: &str = "http://127.0.0.1:11434/api/tags";
const PEER_TIMEOUT: Duration = Duration::from_secs(60);

// Replace lazy_static with once_cell for async Mutex
static LAST_SEEN: Lazy<Arc<Mutex<HashMap<String, DateTime<Utc>>>>> = 
    Lazy::new(|| Arc::new(Mutex::new(HashMap::new())));

static LAST_BROADCAST: Lazy<Arc<Mutex<Option<DateTime<Utc>>>>> = 
    Lazy::new(|| Arc::new(Mutex::new(None)));

/// IPs of peers that advertised LLM=true via UDP — no TCP needed
pub static LLM_UDP_PEERS: Lazy<Arc<Mutex<HashSet<String>>>> =
    Lazy::new(|| Arc::new(Mutex::new(HashSet::new())));

#[derive(Debug, Serialize, Deserialize)]
struct BroadcastMessage {
    message_type: String,
    has_llm: bool,
    timestamp: DateTime<Utc>,
}

// Check if Ollama is running
async fn is_ollama_available() -> bool {
    if let Ok(client) = Client::builder()
        .timeout(Duration::from_secs(2))
        .build() 
    {
        match client.get(OLLAMA_CHECK_URL).send().await {
            Ok(response) => response.status().is_success(),
            Err(_) => false,
        }
    } else {
        false
    }
}

async fn send_broadcast(broadcast_addr: String) -> Result<(), std::io::Error> {
    let socket = UdpSocket::bind("0.0.0.0:0").await?;
    socket.set_broadcast(true)?;
    
    let has_llm = is_ollama_available().await;
    let message = BroadcastMessage {
        message_type: "ONLINE".to_string(),
        has_llm,
        timestamp: Utc::now(),
    };
    
    let message_bytes = serde_json::to_string(&message)
        .map_err(|e| std::io::Error::new(std::io::ErrorKind::Other, e))?
        .into_bytes();
    
    // Only print broadcast message once per interval using async Mutex
    let mut last_broadcast = LAST_BROADCAST.lock().await;
    let now = Utc::now();
    if last_broadcast.is_none() || 
       now.signed_duration_since(last_broadcast.unwrap()).num_seconds() >= BROADCAST_INTERVAL.as_secs() as i64 {
        println!("UDP: Broadcasting to {} (LLM available: {})", broadcast_addr, has_llm);
        *last_broadcast = Some(now);
    }
    
    socket.send_to(&message_bytes, broadcast_addr).await?;
    Ok(())
}

pub async fn periodic_broadcast() {
    let mut interval = interval(BROADCAST_INTERVAL);
    loop {
        interval.tick().await;
        if let Ok(adapters) = get_adapters() {
            for adapter in adapters {
                if adapter.oper_status() == ipconfig::OperStatus::IfOperStatusUp {
                    for ip_addr in adapter.ip_addresses() {
                        if let IpAddr::V4(_ipv4_addr) = ip_addr {
                            if let Some(ipv4) = adapter.ip_addresses().iter().find_map(|ip| match ip {
                                IpAddr::V4(ipv4) => Some(ipv4),
                                _ => None,
                            }) {
                                let octets = ipv4.octets();
                                
                                let bcasts = vec![
                                    // /24 broadcast
                                    format!("{}:{}", Ipv4Addr::new(octets[0], octets[1], octets[2], 255), BROADCAST_PORT),
                                    // /16 broadcast
                                    format!("{}:{}", Ipv4Addr::new(octets[0], octets[1], 255, 255), BROADCAST_PORT),
                                    // Global broadcast
                                    format!("{}:{}", Ipv4Addr::new(255, 255, 255, 255), BROADCAST_PORT),
                                ];
                                
                                for broadcast_addr in bcasts {
                                    if let Err(e) = send_broadcast(broadcast_addr).await {
                                        eprintln!("UDP: Broadcast error: {}", e);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

pub async fn receive_broadcast(received_ips: Arc<Mutex<HashSet<String>>>) -> Result<(), std::io::Error> {
    println!("UDP: Listening on {}", LISTEN_ADDR);
    let socket = UdpSocket::bind(LISTEN_ADDR).await?;
    let mut buf = [0; 1024];

    loop {
        let (size, src) = socket.recv_from(&mut buf).await?;
        if let Ok(message_str) = String::from_utf8(buf[..size].to_vec()) {
            if let Ok(broadcast_msg) = serde_json::from_str::<BroadcastMessage>(&message_str) {
                let ip = src.ip().to_string();
                if !is_my_ip(&ip) {
                    let mut last_seen = LAST_SEEN.lock().await;
                    let now = Utc::now();
                    
                    // Only process if we haven't seen this peer recently
                    if !last_seen.contains_key(&ip) || 
                       now.signed_duration_since(*last_seen.get(&ip).unwrap()).num_seconds() >= PEER_TIMEOUT.as_secs() as i64 {
                        println!("UDP: Discovered peer {} (LLM available: {})", ip, broadcast_msg.has_llm);
                        last_seen.insert(ip.clone(), now);
                        
                        let mut ips = received_ips.lock().await;
                        ips.insert(ip.clone());

                        // Track LLM-capable peers separately for fallback
                        if broadcast_msg.has_llm {
                            let mut llm_peers = LLM_UDP_PEERS.lock().await;
                            llm_peers.insert(ip);
                        }
                    }
                }
            }
        }
    }
}
