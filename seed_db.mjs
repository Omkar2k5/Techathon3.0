/**
 * EduNet Lab — Node.js seed script
 * Run: node seed_db.mjs
 * Requires: npm install mongodb (already in Cargo deps on Rust side, but need node mongodb driver)
 */
import { MongoClient, ObjectId } from "mongodb";

const MONGO_URI = "mongodb://localhost:27017";
const DB_NAME = "neuromesh_lab";

async function seed() {
    const client = new MongoClient(MONGO_URI);
    await client.connect();
    const db = client.db(DB_NAME);
    console.log("Connected to MongoDB:", DB_NAME);

    // ── Clear ──
    await Promise.all([
        db.collection("users").drop().catch(() => { }),
        db.collection("subjects").drop().catch(() => { }),
        db.collection("subject_enrollments").drop().catch(() => { }),
        db.collection("assignments").drop().catch(() => { }),
        db.collection("submissions").drop().catch(() => { }),
    ]);
    console.log("Cleared existing collections");

    // ── Teachers ──
    const t1res = await db.collection("users").insertOne({
        name: "Prof. Ahmed Khan", email: "ahmed@edunet.in",
        roll_no: null, password: "teacher123", role: "teacher",
        created_at: new Date(),
    });
    const t2res = await db.collection("users").insertOne({
        name: "Dr. Priya Sharma", email: "priya@edunet.in",
        roll_no: null, password: "teacher123", role: "teacher",
        created_at: new Date(),
    });
    const teacher1Id = t1res.insertedId;
    const teacher2Id = t2res.insertedId;
    console.log("Inserted teachers");

    // ── Students ──
    const studentDocs = [
        { name: "Aarav Mehta", roll_no: "21AIML101" },
        { name: "Sneha Patil", roll_no: "21AIML102" },
        { name: "Rohan Desai", roll_no: "21AIML103" },
        { name: "Prachi Joshi", roll_no: "21AIML104" },
        { name: "Kabir Singh", roll_no: "21CS201" },
        { name: "Tanvi Naik", roll_no: "21CS202" },
    ];
    const studentIds = [];
    for (const s of studentDocs) {
        const res = await db.collection("users").insertOne({
            name: s.name,
            email: `${s.roll_no.toLowerCase()}@student.edunet.in`,
            roll_no: s.roll_no, password: "student123", role: "student",
            created_at: new Date(),
        });
        studentIds.push(res.insertedId);
    }
    console.log("Inserted", studentIds.length, "students");

    // ── Subjects ──
    const sub1 = await db.collection("subjects").insertOne({
        subject_name: "Data Structures & Algorithms",
        subject_code: "CS301", teacher_id: teacher1Id,
        is_active: true, created_at: new Date(),
    });
    const sub2 = await db.collection("subjects").insertOne({
        subject_name: "Python Programming",
        subject_code: "CS101", teacher_id: teacher1Id,
        is_active: true, created_at: new Date(),
    });
    const sub3 = await db.collection("subjects").insertOne({
        subject_name: "Computer Networks",
        subject_code: "CS401", teacher_id: teacher2Id,
        is_active: true, created_at: new Date(),
    });
    console.log("Inserted subjects: CS301, CS101, CS401");

    // ── Enrollments ──
    const aimlStudents = studentIds.slice(0, 4);
    const csStudents = studentIds.slice(4, 6);

    const enrollments = [];
    for (const sid of aimlStudents) {
        enrollments.push({ student_id: sid, subject_id: sub1.insertedId, enrolled_at: new Date() });
        enrollments.push({ student_id: sid, subject_id: sub2.insertedId, enrolled_at: new Date() });
    }
    for (const sid of csStudents) {
        enrollments.push({ student_id: sid, subject_id: sub1.insertedId, enrolled_at: new Date() });
        enrollments.push({ student_id: sid, subject_id: sub3.insertedId, enrolled_at: new Date() });
    }
    await db.collection("subject_enrollments").insertMany(enrollments);
    console.log("Inserted", enrollments.length, "enrollments");

    // ── Summary ──
    console.log("\n=== SEED COMPLETE ===");
    console.log("Login credentials:");
    console.log("  STAFF   : email=ahmed@edunet.in   password=teacher123");
    console.log("  STAFF   : email=priya@edunet.in   password=teacher123");
    console.log("  STUDENT : roll_no=21AIML101       password=student123");
    console.log("  STUDENT : roll_no=21AIML102       password=student123");
    console.log("  STUDENT : roll_no=21CS201         password=student123");
    console.log("Users:", await db.collection("users").countDocuments());
    console.log("Subjects:", await db.collection("subjects").countDocuments());
    console.log("Enrollments:", await db.collection("subject_enrollments").countDocuments());

    await client.close();
}

seed().catch(err => { console.error("Seed failed:", err); process.exit(1); });
