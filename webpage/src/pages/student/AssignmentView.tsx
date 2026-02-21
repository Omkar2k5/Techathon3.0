import { useState, useEffect, useRef } from "react";
import { useParams, useNavigate } from "react-router-dom";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { Card, CardContent } from "@/components/ui/card";
import {
  ArrowLeft, Upload, Download, CheckCircle, Clock,
  AlertCircle, Loader2, FileCode, GraduationCap, User, Timer
} from "lucide-react";
import { useAuth } from "@/lib/auth";
import {
  getActiveAssignments, uploadFile, getSubmissionStatus,
  sampleFileUrl, type ActiveAssignment
} from "@/lib/api";
import { useToast } from "@/hooks/use-toast";

/** Personal countdown — starts from the moment the page mounts */
function PersonalTimer({ limitMinutes }: { limitMinutes: number }) {
  const endMs = useRef(Date.now() + limitMinutes * 60 * 1000);
  const [label, setLabel] = useState("");
  const [pct, setPct] = useState(100);
  const [expired, setExpired] = useState(false);

  useEffect(() => {
    const total = limitMinutes * 60 * 1000;
    const update = () => {
      const diff = endMs.current - Date.now();
      if (diff <= 0) { setLabel("Time limit reached"); setPct(0); setExpired(true); return; }
      const h = Math.floor(diff / 3600000);
      const m = Math.floor((diff % 3600000) / 60000);
      const s = Math.floor((diff % 60000) / 1000);
      setLabel(h > 0 ? `${h}h ${m}m ${s}s` : `${m}m ${s}s`);
      setPct(Math.max(0, (diff / total) * 100));
    };
    update();
    const t = setInterval(update, 1000);
    return () => clearInterval(t);
  }, [limitMinutes]);

  const barColor = expired ? "bg-destructive" : pct > 40 ? "bg-primary" : pct > 15 ? "bg-orange-500" : "bg-red-500";
  return (
    <div className="space-y-1.5">
      <div className={`flex items-center gap-2 text-sm font-semibold ${expired ? "text-destructive" : "text-foreground"}`}>
        <Timer className="w-4 h-4" />
        Personal timer: {label} remaining
      </div>
      <div className="w-full h-2 bg-secondary rounded-full overflow-hidden">
        <div className={`h-full rounded-full transition-[width] duration-1000 ${barColor}`} style={{ width: `${pct}%` }} />
      </div>
    </div>
  );
}

/** Hard deadline countdown — based on server deadline */
function DeadlineTimer({ deadline }: { deadline: string }) {
  const [label, setLabel] = useState("");
  const [pct, setPct] = useState(100);
  const [expired, setExpired] = useState(false);

  useEffect(() => {
    const mountTime = Date.now();
    const end = new Date(deadline).getTime();
    const total = Math.max(1, end - mountTime);
    const update = () => {
      const diff = end - Date.now();
      if (diff <= 0) { setLabel("Submissions closed"); setPct(0); setExpired(true); return; }
      const h = Math.floor(diff / 3600000);
      const m = Math.floor((diff % 3600000) / 60000);
      const s = Math.floor((diff % 60000) / 1000);
      setLabel(h > 0 ? `${h}h ${m}m ${s}s` : `${m}m ${s}s`);
      setPct(Math.max(0, (diff / total) * 100));
    };
    update();
    const t = setInterval(update, 1000);
    return () => clearInterval(t);
  }, [deadline]);

  const barColor = expired ? "bg-destructive" : pct > 40 ? "bg-blue-500" : pct > 15 ? "bg-orange-500" : "bg-red-500";
  return (
    <div className="space-y-1.5">
      <div className={`flex items-center gap-2 text-sm font-semibold ${expired ? "text-destructive" : "text-blue-600 dark:text-blue-400"}`}>
        <Clock className="w-4 h-4" />
        Hard deadline: {expired ? "Closed" : label + " left"}
      </div>
      <div className="w-full h-2 bg-secondary rounded-full overflow-hidden">
        <div className={`h-full rounded-full transition-[width] duration-1000 ${barColor}`} style={{ width: `${pct}%` }} />
      </div>
      <p className="text-xs text-muted-foreground">
        Closes {new Date(deadline).toLocaleString(undefined, { dateStyle: "medium", timeStyle: "short" })}
      </p>
    </div>
  );
}

const AssignmentView = () => {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const { user } = useAuth();
  const { toast } = useToast();

  const [assignment, setAssignment] = useState<ActiveAssignment | null>(null);
  const [loading, setLoading] = useState(true);
  const [uploading, setUploading] = useState(false);
  const [submitted, setSubmitted] = useState<{ file_name: string; submitted_at: string } | null>(null);
  const [dragOver, setDragOver] = useState(false);
  const fileRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    if (!user || !id) return;
    const load = async () => {
      const assignments = await getActiveAssignments(user.id);
      const found = assignments.find(a => a.id === id) ?? null;
      setAssignment(found);
      setLoading(false);
      if (found) {
        const status = await getSubmissionStatus(found.id, user.id);
        if (status) setSubmitted({ file_name: status.file_name, submitted_at: status.submitted_at });
      }
    };
    load();
  }, [user, id]);

  const handleUpload = async (file: File) => {
    if (!assignment || !user) return;
    const ext = "." + file.name.split(".").pop()?.toLowerCase();
    if (!assignment.allowed_file_types.includes(ext)) {
      toast({ title: "Invalid file type", description: `Allowed: ${assignment.allowed_file_types.join(", ")}`, variant: "destructive" });
      return;
    }
    setUploading(true);
    try {
      const fd = new FormData();
      fd.append("file", file);
      fd.append("assignment_id", assignment.id);
      fd.append("student_id", user.id);
      fd.append("roll_no", user.roll_no ?? user.id);
      await uploadFile(fd);
      const status = await getSubmissionStatus(assignment.id, user.id);
      if (status) setSubmitted({ file_name: status.file_name, submitted_at: status.submitted_at });
      toast({ title: "Submitted!", description: `${file.name} uploaded successfully.` });
    } catch (err: any) {
      toast({ title: "Upload failed", description: err.message, variant: "destructive" });
    } finally { setUploading(false); }
  };

  if (loading) return (
    <div className="min-h-screen flex items-center justify-center">
      <Loader2 className="w-6 h-6 animate-spin text-muted-foreground" />
    </div>
  );

  if (!assignment) return (
    <div className="min-h-screen flex flex-col items-center justify-center gap-4">
      <AlertCircle className="w-12 h-12 text-muted-foreground opacity-40" />
      <p className="text-sm text-muted-foreground">This lab session is not active or not found.</p>
      <Button variant="outline" onClick={() => navigate("/student/dashboard")}>Go back</Button>
    </div>
  );

  return (
    <div className="min-h-screen bg-background">
      <header className="border-b border-border bg-card sticky top-0 z-10">
        <div className="container flex items-center gap-4 h-16 px-6">
          <Button variant="ghost" size="icon" onClick={() => navigate("/student/dashboard")}>
            <ArrowLeft className="w-4 h-4" />
          </Button>
          <div className="flex-1 min-w-0">
            <div className="flex items-center gap-2">
              <span className="font-mono text-xs px-2 py-0.5 bg-secondary rounded shrink-0">{assignment.subject_code}</span>
              <h1 className="text-sm font-bold truncate">{assignment.subject_name}</h1>
            </div>
            <p className="text-xs text-muted-foreground">{assignment.assignment_name}</p>
          </div>
          <Badge className="bg-primary text-primary-foreground gap-1 text-xs shrink-0">
            <span className="w-1.5 h-1.5 bg-primary-foreground rounded-full animate-pulse" />LIVE
          </Badge>
        </div>
      </header>

      <main className="container px-6 py-8 max-w-2xl mx-auto space-y-6">
        {/* Assignment info card */}
        <Card className="border-border">
          <CardContent className="p-6 space-y-4">
            <div className="flex items-start justify-between">
              <div>
                <h2 className="text-lg font-bold">{assignment.assignment_name}</h2>
                <p className="text-sm text-muted-foreground mt-0.5">{assignment.subject_name}</p>
              </div>
              <span className="font-mono text-xs px-2 py-1 bg-secondary rounded">{assignment.subject_code}</span>
            </div>

            {/* Teacher info */}
            {assignment.teacher_name && (
              <div className="flex items-center gap-2 py-3 px-4 bg-secondary/50 rounded-lg">
                <User className="w-4 h-4 text-muted-foreground shrink-0" />
                <div className="text-sm">
                  <span className="font-medium">{assignment.teacher_name}</span>
                  <span className="text-muted-foreground text-xs ml-2">· Uploaded this lab</span>
                </div>
              </div>
            )}

            {/* Dual timer */}
            <div className="space-y-4 p-4 bg-secondary/30 rounded-xl">
              <PersonalTimer limitMinutes={assignment.time_limit_minutes} />
              <div className="border-t border-border pt-3">
                <DeadlineTimer deadline={assignment.deadline} />
              </div>
            </div>

            {/* Meta */}
            <div className="flex items-center gap-4 text-xs text-muted-foreground">
              <span className="flex items-center gap-1"><FileCode className="w-3.5 h-3.5" />Allowed: {assignment.allowed_file_types.join(", ")}</span>
            </div>

            {/* Sample download */}
            {assignment.has_sample && (
              <Button variant="outline" className="w-full gap-2" asChild>
                <a href={sampleFileUrl(assignment.id)} download>
                  <Download className="w-4 h-4" />Download Sample File
                </a>
              </Button>
            )}
          </CardContent>
        </Card>

        {/* Submission status */}
        {submitted && (
          <Card className="border-green-300 dark:border-green-800 bg-green-50 dark:bg-green-950/30">
            <CardContent className="p-5 flex items-start gap-3">
              <CheckCircle className="w-5 h-5 text-green-600 shrink-0 mt-0.5" />
              <div>
                <p className="text-sm font-semibold text-green-800 dark:text-green-300">Submitted successfully</p>
                <p className="text-xs text-green-700 dark:text-green-400 font-mono mt-0.5">{submitted.file_name}</p>
                <p className="text-xs text-green-600 dark:text-green-500 mt-0.5">
                  {new Date(submitted.submitted_at).toLocaleString()}
                </p>
                <p className="text-xs text-muted-foreground mt-2">You can resubmit to replace with a newer version.</p>
              </div>
            </CardContent>
          </Card>
        )}

        {/* Upload area */}
        <Card className="border-border">
          <CardContent className="p-6">
            <h3 className="font-semibold text-sm mb-4 flex items-center gap-2">
              <Upload className="w-4 h-4" />{submitted ? "Resubmit Solution" : "Submit Your Solution"}
            </h3>
            <div
              className={`border-2 border-dashed rounded-xl p-10 text-center transition-colors cursor-pointer
                ${dragOver ? "border-primary bg-primary/5" : "border-border hover:border-foreground/30"}`}
              onDragOver={e => { e.preventDefault(); setDragOver(true); }}
              onDragLeave={() => setDragOver(false)}
              onDrop={e => {
                e.preventDefault(); setDragOver(false);
                const f = e.dataTransfer.files[0];
                if (f) handleUpload(f);
              }}
              onClick={() => fileRef.current?.click()}
            >
              {uploading ? (
                <><Loader2 className="w-8 h-8 mx-auto text-primary animate-spin mb-2" /><p className="text-sm">Uploading...</p></>
              ) : (
                <>
                  <Upload className="w-8 h-8 mx-auto text-muted-foreground mb-3" />
                  <p className="text-sm font-medium">Drop file here or click to browse</p>
                  <p className="text-xs text-muted-foreground mt-1">Allowed: {assignment.allowed_file_types.join(", ")}</p>
                </>
              )}
              <input ref={fileRef} type="file" className="hidden"
                accept={assignment.allowed_file_types.join(",")}
                onChange={e => { const f = e.target.files?.[0]; if (f) handleUpload(f); }} />
            </div>
          </CardContent>
        </Card>
      </main>
    </div>
  );
};

export default AssignmentView;
