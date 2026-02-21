use tokio::time::{interval, Duration};
use mongodb::bson::doc;
use chrono::Utc;
use crate::lab_module::mongo_connection::get_db;

/// Background task: every 30 seconds close any assignments whose deadline has passed.
pub async fn run_scheduler() {
    let mut tick = interval(Duration::from_secs(30));
    loop {
        tick.tick().await;
        let db = get_db();
        let col = db.collection::<mongodb::bson::Document>("assignments");
        let now_str = Utc::now().to_rfc3339();

        // Find all active assignments
        let filter = doc! { "is_active": true };
        use futures::TryStreamExt;
        if let Ok(mut cur) = col.find(filter, None).await {
            while let Ok(Some(doc)) = cur.try_next().await {
                let deadline_str = doc.get_str("deadline").unwrap_or("");
                if deadline_str.is_empty() { continue; }
                if let Ok(deadline) = chrono::DateTime::parse_from_rfc3339(deadline_str) {
                    if Utc::now() > deadline {
                        if let Ok(id) = doc.get_object_id("_id") {
                            let update = mongodb::bson::doc! { "$set": { "is_active": false } };
                            let _ = col.update_one(mongodb::bson::doc! { "_id": id }, update, None).await;
                            println!("LAB SCHEDULER: Closed expired assignment {}", id.to_hex());
                        }
                    }
                }
            }
        }
    }
}
