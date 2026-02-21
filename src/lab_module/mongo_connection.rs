use mongodb::{Client, options::ClientOptions, Database};
use once_cell::sync::OnceCell;

static DB: OnceCell<Database> = OnceCell::new();

pub async fn init_db() -> Result<(), Box<dyn std::error::Error>> {
    let opts = ClientOptions::parse(
        "mongodb+srv://root:root123@techathon.h1ehyle.mongodb.net/techathon?retryWrites=true&w=majority"
    ).await?;
    let client = Client::with_options(opts)?;
    let db = client.database("techathon");
    DB.set(db).ok();
    println!("LAB: MongoDB connected (techathon @ Atlas)");
    Ok(())
}

pub fn get_db() -> &'static Database {
    DB.get().expect("MongoDB not initialized — call init_db() first")
}
