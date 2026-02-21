import { useState, useEffect, useRef } from "react";
import { useParams, useNavigate } from "react-router-dom";
import { Card, CardContent } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Badge } from "@/components/ui/badge";
import { Progress } from "@/components/ui/progress";
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogTrigger } from "@/components/ui/dialog";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import {
  ArrowLeft, Plus, Play, Clock, FileCode, Upload, Users,
  CheckCircle, Timer, Square, Loader2, Download, RefreshCw,
  Pencil, UserPlus, X, ChevronDown, ChevronUp
} from "lucide-react";
import { useAuth } from "@/lib/auth";
import {
  getSubjectAssignments, createAssignment, startAssignment, editAssignment,
  closeAssignment, getSubmissions, exportCsvUrl, getTeacherSubjects,
  getSubjectStudents, enrollStudent, removeStudent, submissionDownloadUrl,
  type Assignment, type Submission, type Subject, type EnrolledStudent
} from "@/lib/api";
import { useToast } from "@/hooks/use-toast";

function LiveTimer({ deadline, onExpire }: { deadline: string; onExpire?: () => void }) {
  const [label, setLabel] = useState("");
  const [pct, setPct] = useState(100);
  useEffect(() => {
    const mountTime = Date.now();
    const end = new Date(deadline).getTime();
    const total = Math.max(1, end - mountTime);
    const update = () => {
      const diff = end - Date.now();
      if (diff <= 0) { setLabel("Deadline reached"); setPct(0); onExpire?.(); return; }
      const h = Math.floor(diff / 3600000);
      const m = Math.floor((diff % 3600000) / 60000);
      const s = Math.floor((diff % 60000) / 1000);
      setLabel(h > 0 ? `${h}h ${m}m ${s}s left` : `${m}m ${s}s left`);
      setPct(Math.max(0, (diff / total) * 100));
    };
    update();
    const t = setInterval(update, 1000);
    return () => clearInterval(t);
  }, [deadline]);
  const barColor = pct > 40 ? "bg-primary" : pct > 15 ? "bg-orange-500" : "bg-red-500";
  return (
    <div className="space-y-1.5">
      <div className="flex items-center gap-1.5 text-xs font-semibold text-foreground">
        <Clock className="w-3.5 h-3.5" />{label}
      </div>
      <div className="w-full h-1.5 bg-secondary rounded-full overflow-hidden">
        <div className={`h-full rounded-full transition-[width] duration-1000 ${barColor}`} style={{ width: `${pct}%` }} />
      </div>
    </div>
  );
}

const SubjectDetail = () => {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const { user } = useAuth();
  const { toast } = useToast();

  const [subject, setSubject] = useState<Subject | null>(null);
  const [assignments, setAssignments] = useState<Assignment[]>([]);
  const [submissions, setSubmissions] = useState<Record<string, Submission[]>>({});
  const [students, setStudents] = useState<EnrolledStudent[]>([]);
  const [showStudents, setShowStudents] = useState(false);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState<Record<string, boolean>>({});
  const [dialogOpen, setDialogOpen] = useState(false);
  const [creating, setCreating] = useState(false);
  const fileRef = useRef<HTMLInputElement>(null);

  // Edit assignment state
  const [editingId, setEditingId] = useState<string | null>(null);
  const [editForm, setEditForm] = useState({ assignment_name: "", time_limit_minutes: "", deadline: "" });
  const [saving, setSaving] = useState(false);

  // Enroll student state
  const [enrollDialogOpen, setEnrollDialogOpen] = useState(false);
  const [rollNoInput, setRollNoInput] = useState("");
  const [enrolling, setEnrolling] = useState(false);

  const [form, setForm] = useState({
    name: "", allowedTypes: ".cpp", timeLimit: "120", deadline: "", sampleFile: null as File | null,
  });

  useEffect(() => {
    if (user && id) { fetchSubject(); fetchAssignments(); fetchStudents(); }
  }, [user, id]);

  useEffect(() => {
    const active = assignments.filter(a => a.is_active);
    if (active.length === 0) return;
    active.forEach(a => fetchSubs(a.id));
    const t = setInterval(() => active.forEach(a => fetchSubs(a.id)), 15000);
    return () => clearInterval(t);
  }, [assignments]);

  const fetchSubject = async () => {
    const all = await getTeacherSubjects(user!.id);
    setSubject(all.find(s => s.id === id) ?? null);
  };
  const fetchAssignments = async () => {
    setLoading(true);
    try { const data = await getSubjectAssignments(id!); setAssignments(data); }
    finally { setLoading(false); }
  };
  const fetchStudents = async () => {
    const data = await getSubjectStudents(id!);
    setStudents(data);
  };
  const fetchSubs = async (assignmentId: string, manual = false) => {
    if (manual) setRefreshing(r => ({ ...r, [assignmentId]: true }));
    const subs = await getSubmissions(assignmentId);
    setSubmissions(prev => ({ ...prev, [assignmentId]: subs }));
    if (manual) setRefreshing(r => ({ ...r, [assignmentId]: false }));
  };

  const handleCreate = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!form.name || !form.deadline) {
      toast({ title: "Missing fields", description: "Name and deadline are required.", variant: "destructive" }); return;
    }
    setCreating(true);
    try {
      const fd = new FormData();
      fd.append("subject_id", id!);
      fd.append("assignment_name", form.name);
      fd.append("allowed_file_types", JSON.stringify(form.allowedTypes.split(",")));
      fd.append("time_limit_minutes", form.timeLimit);
      fd.append("deadline", new Date(form.deadline).toISOString());
      fd.append("created_by", user!.id);
      if (form.sampleFile) fd.append("sample_file", form.sampleFile);
      await createAssignment(fd);
      setDialogOpen(false);
      setForm({ name: "", allowedTypes: ".cpp", timeLimit: "120", deadline: "", sampleFile: null });
      fetchAssignments();
      toast({ title: "Assignment created", description: "Click 'Start Lab' when ready to go live." });
    } catch (err: any) {
      toast({ title: "Error creating assignment", description: err.message, variant: "destructive" });
    } finally { setCreating(false); }
  };

  const handleStart = async (assignment: Assignment) => {
    try {
      await startAssignment(assignment.id);
      fetchAssignments();
      toast({ title: "Lab is now LIVE!", description: "Students can see and submit this assignment." });
    } catch (err: any) {
      toast({ title: "Failed to start lab", description: err.message, variant: "destructive" });
    }
  };

  const handleClose = async (assignmentId: string) => {
    await closeAssignment(assignmentId);
    fetchAssignments();
    toast({ title: "Lab closed", description: "No more submissions accepted." });
  };

  const openEdit = (a: Assignment) => {
    setEditingId(a.id);
    setEditForm({
      assignment_name: a.assignment_name,
      time_limit_minutes: String(a.time_limit_minutes),
      deadline: a.deadline ? new Date(a.deadline).toISOString().slice(0, 16) : "",
    });
  };

  const handleSaveEdit = async () => {
    if (!editingId) return;
    setSaving(true);
    try {
      await editAssignment(editingId, {
        assignment_name: editForm.assignment_name,
        time_limit_minutes: parseInt(editForm.time_limit_minutes),
        deadline: editForm.deadline ? new Date(editForm.deadline).toISOString() : undefined,
      });
      setEditingId(null);
      fetchAssignments();
      toast({ title: "Assignment updated" });
    } catch (err: any) {
      toast({ title: "Update failed", description: err.message, variant: "destructive" });
    } finally { setSaving(false); }
  };

  const handleEnroll = async (e: React.FormEvent) => {
    e.preventDefault();
    setEnrolling(true);
    try {
      const res = await enrollStudent(id!, rollNoInput.trim());
      setRollNoInput("");
      fetchStudents();
      toast({ title: `${res.name} enrolled successfully` });
    } catch (err: any) {
      toast({ title: "Enroll failed", description: err.message, variant: "destructive" });
    } finally { setEnrolling(false); }
  };

  const handleRemoveStudent = async (studentId: string, name: string) => {
    try {
      await removeStudent(id!, studentId);
      fetchStudents();
      toast({ title: `${name} removed from subject` });
    } catch {
      toast({ title: "Failed to remove student", variant: "destructive" });
    }
  };

  return (
    <div className="min-h-screen bg-background">
      <header className="border-b border-border bg-card sticky top-0 z-10">
        <div className="container flex items-center gap-4 h-16 px-6">
          <Button variant="ghost" size="icon" onClick={() => navigate("/staff/dashboard")}>
            <ArrowLeft className="w-4 h-4" />
          </Button>
          <div className="flex-1 min-w-0">
            <div className="flex items-center gap-2">
              <h1 className="text-sm font-bold truncate">{subject?.subject_name ?? "Loading..."}</h1>
              {subject && <span className="font-mono text-xs px-2 py-0.5 bg-secondary rounded shrink-0">{subject.subject_code}</span>}
            </div>
            <p className="text-xs text-muted-foreground">
              {assignments.filter(a => a.is_active).length > 0
                ? `${assignments.filter(a => a.is_active).length} live session · auto-refreshing`
                : "No active session"}
              {" · "}
              <button className="underline" onClick={() => setShowStudents(v => !v)}>
                {students.length} student{students.length !== 1 ? "s" : ""}
              </button>
            </p>
          </div>
          <Button variant="outline" size="sm" className="gap-2 shrink-0" onClick={() => setEnrollDialogOpen(true)}>
            <UserPlus className="w-4 h-4" />
            Add Student
          </Button>
          <Dialog open={dialogOpen} onOpenChange={setDialogOpen}>
            <DialogTrigger asChild>
              <Button size="sm" className="gap-2 shrink-0"><Plus className="w-4 h-4" />Create Assignment</Button>
            </DialogTrigger>
            <DialogContent className="sm:max-w-md">
              <DialogHeader><DialogTitle>Create New Assignment</DialogTitle></DialogHeader>
              <form onSubmit={handleCreate} className="space-y-4 mt-4">
                <div className="space-y-2">
                  <Label>Assignment Name *</Label>
                  <Input placeholder="e.g. Linked List Implementation" value={form.name}
                    onChange={e => setForm(f => ({ ...f, name: e.target.value }))} />
                </div>
                <div className="space-y-2">
                  <Label>Sample File (optional)</Label>
                  <div className="border-2 border-dashed border-border rounded-lg p-4 text-center hover:border-foreground/30 cursor-pointer"
                    onClick={() => fileRef.current?.click()}>
                    <Upload className="w-6 h-6 mx-auto text-muted-foreground mb-1" />
                    <p className="text-xs text-muted-foreground">{form.sampleFile ? form.sampleFile.name : "Click to upload"}</p>
                    <input ref={fileRef} type="file" className="hidden"
                      onChange={e => setForm(f => ({ ...f, sampleFile: e.target.files?.[0] ?? null }))} />
                  </div>
                </div>
                <div className="space-y-2">
                  <Label>Allowed Types</Label>
                  <Select value={form.allowedTypes} onValueChange={v => setForm(f => ({ ...f, allowedTypes: v }))}>
                    <SelectTrigger><SelectValue /></SelectTrigger>
                    <SelectContent>
                      <SelectItem value=".cpp">.cpp</SelectItem>
                      <SelectItem value=".py">.py</SelectItem>
                      <SelectItem value=".java">.java</SelectItem>
                      <SelectItem value=".c">.c</SelectItem>
                      <SelectItem value=".cpp,.py">.cpp &amp; .py</SelectItem>
                      <SelectItem value=".cpp,.py,.java,.c">All common</SelectItem>
                    </SelectContent>
                  </Select>
                </div>
                <div className="space-y-2">
                  <Label>Hard Deadline *</Label>
                  <Input type="datetime-local" value={form.deadline}
                    onChange={e => setForm(f => ({ ...f, deadline: e.target.value }))} />
                </div>
                <Button className="w-full gap-2" type="submit" disabled={creating}>
                  {creating ? <><Loader2 className="w-4 h-4 animate-spin" />Creating...</> : <><Plus className="w-4 h-4" />Create Assignment</>}
                </Button>
              </form>
            </DialogContent>
          </Dialog>
        </div>
      </header>

      {/* Enroll student dialog */}
      <Dialog open={enrollDialogOpen} onOpenChange={setEnrollDialogOpen}>
        <DialogContent className="sm:max-w-sm">
          <DialogHeader><DialogTitle>Add Student</DialogTitle></DialogHeader>
          <form onSubmit={handleEnroll} className="space-y-4 mt-2">
            <div className="space-y-2">
              <Label>Student Roll Number</Label>
              <Input placeholder="e.g. 2023CS001" value={rollNoInput}
                onChange={e => setRollNoInput(e.target.value)} />
            </div>
            <Button type="submit" className="w-full" disabled={enrolling || !rollNoInput.trim()}>
              {enrolling ? <><Loader2 className="w-4 h-4 mr-2 animate-spin" />Enrolling...</> : "Enroll Student"}
            </Button>
          </form>
        </DialogContent>
      </Dialog>

      <main className="container px-6 py-8 space-y-6">
        {/* Student roster panel */}
        {showStudents && (
          <Card className="border-border">
            <CardContent className="p-5">
              <div className="flex items-center justify-between mb-4">
                <h3 className="font-semibold text-sm flex items-center gap-2">
                  <Users className="w-4 h-4" />Enrolled Students ({students.length})
                </h3>
                <Button variant="ghost" size="icon" className="h-6 w-6" onClick={() => setShowStudents(false)}>
                  <ChevronUp className="w-4 h-4" />
                </Button>
              </div>
              {students.length === 0 ? (
                <p className="text-xs text-muted-foreground text-center py-4">No students enrolled. Use "Add Student" to enrol by roll number.</p>
              ) : (
                <div className="space-y-2 max-h-60 overflow-y-auto">
                  {students.map(s => (
                    <div key={s.id} className="flex items-center justify-between py-2 px-3 bg-secondary/40 rounded-lg">
                      <div>
                        <p className="text-sm font-medium">{s.name}</p>
                        <p className="text-xs text-muted-foreground font-mono">{s.roll_no}</p>
                      </div>
                      <Button variant="ghost" size="icon" className="h-7 w-7 text-destructive hover:bg-destructive/10"
                        onClick={() => handleRemoveStudent(s.id, s.name)}>
                        <X className="w-3.5 h-3.5" />
                      </Button>
                    </div>
                  ))}
                </div>
              )}
            </CardContent>
          </Card>
        )}

        {/* Assignments */}
        {loading ? (
          <div className="flex items-center justify-center py-20">
            <Loader2 className="w-6 h-6 animate-spin text-muted-foreground" />
          </div>
        ) : (
          <div className="space-y-4">
            {assignments.length === 0 && (
              <div className="text-center py-20 text-muted-foreground">
                <FileCode className="w-12 h-12 mx-auto mb-4 opacity-30" />
                <p className="text-sm font-medium">No assignments yet</p>
                <p className="text-xs mt-1">Create your first assignment to get started</p>
              </div>
            )}
            {assignments.map((assignment) => {
              const subs = submissions[assignment.id] ?? [];
              const isEditing = editingId === assignment.id;
              return (
                <Card key={assignment.id} className={`border-border overflow-hidden ${assignment.is_active ? "ring-1 ring-primary/20" : ""}`}>
                  <CardContent className="p-0">
                    <div className="px-6 py-5 flex items-start justify-between gap-4">
                      <div className="space-y-2 flex-1 min-w-0">
                        {isEditing ? (
                          <div className="space-y-3">
                            <Input value={editForm.assignment_name}
                              onChange={e => setEditForm(f => ({ ...f, assignment_name: e.target.value }))}
                              className="font-semibold" />
                            <div className="space-y-1">
                              <Label className="text-xs">Hard Deadline</Label>
                              <Input type="datetime-local" value={editForm.deadline}
                                onChange={e => setEditForm(f => ({ ...f, deadline: e.target.value }))} />
                            </div>
                            <div className="flex gap-2">
                              <Button size="sm" onClick={handleSaveEdit} disabled={saving}>
                                {saving ? <Loader2 className="w-4 h-4 animate-spin" /> : "Save"}
                              </Button>
                              <Button size="sm" variant="ghost" onClick={() => setEditingId(null)}>Cancel</Button>
                            </div>
                          </div>
                        ) : (
                          <>
                            <div className="flex items-center gap-2 flex-wrap">
                              <h3 className="font-semibold text-foreground">{assignment.assignment_name}</h3>
                              {assignment.is_active ? (
                                <Badge className="flex items-center gap-1.5 bg-red-500 text-white text-xs px-2.5 py-0.5">
                                  <span className="w-1.5 h-1.5 bg-white rounded-full animate-pulse" />LIVE
                                </Badge>
                              ) : (
                                <Badge variant="secondary" className="text-xs">Inactive</Badge>
                              )}
                            </div>
                            <div className="flex flex-wrap items-center gap-x-4 gap-y-1 text-xs text-muted-foreground">
                              <span className="flex items-center gap-1"><FileCode className="w-3.5 h-3.5" />{assignment.allowed_file_types.join(", ")}</span>
                              <span className="flex items-center gap-1"><Users className="w-3.5 h-3.5" />{subs.length} submitted</span>
                              <span>Deadline: {new Date(assignment.deadline).toLocaleString()}</span>
                            </div>
                            {assignment.is_active && <LiveTimer deadline={assignment.deadline} onExpire={fetchAssignments} />}
                          </>
                        )}
                      </div>
                      {!isEditing && (
                        <div className="flex items-center gap-2 shrink-0 pt-1">
                          <Button variant="ghost" size="icon" className="h-8 w-8" onClick={() => openEdit(assignment)}>
                            <Pencil className="w-3.5 h-3.5" />
                          </Button>
                          {assignment.is_active ? (
                            <>
                              <Button variant="outline" size="sm" className="gap-1 text-xs h-8"
                                onClick={() => fetchSubs(assignment.id, true)} disabled={refreshing[assignment.id]}>
                                <RefreshCw className={`w-3.5 h-3.5 ${refreshing[assignment.id] ? "animate-spin" : ""}`} />Refresh
                              </Button>
                              <Button variant="outline" size="sm" className="gap-1 text-xs h-8"
                                onClick={() => window.open(exportCsvUrl(assignment.id), "_blank")}>
                                <Download className="w-3.5 h-3.5" />CSV
                              </Button>
                              <Button variant="outline" size="sm"
                                className="gap-1 text-xs h-8 text-destructive border-destructive/30 hover:bg-destructive/10"
                                onClick={() => handleClose(assignment.id)}>
                                <Square className="w-3.5 h-3.5" />End Lab
                              </Button>
                            </>
                          ) : (
                            <>
                              <Button variant="outline" size="sm" className="gap-1 text-xs h-8"
                                onClick={() => window.open(exportCsvUrl(assignment.id), "_blank")}>
                                <Download className="w-3.5 h-3.5" />CSV
                              </Button>
                              <Button size="sm" className="gap-1 text-xs h-8" onClick={() => handleStart(assignment)}>
                                <Play className="w-3.5 h-3.5" />Start Lab
                              </Button>
                            </>
                          )}
                        </div>
                      )}
                    </div>

                    {/* Submissions */}
                    <div className="border-t border-border bg-secondary/20 px-6 py-4">
                      <div className="flex items-center justify-between mb-3">
                        <h4 className="text-xs font-semibold flex items-center gap-1.5">
                          <CheckCircle className="w-3.5 h-3.5" />Submissions ({subs.length})
                        </h4>
                        {!assignment.is_active && (
                          <button onClick={() => fetchSubs(assignment.id, true)}
                            className="text-xs text-muted-foreground hover:text-foreground underline">Load</button>
                        )}
                      </div>
                      {subs.length === 0 ? (
                        <p className="text-xs text-center text-muted-foreground py-3">
                          {assignment.is_active ? "Waiting for submissions..." : "No submissions recorded."}
                        </p>
                      ) : (
                        <div className="space-y-1.5 max-h-52 overflow-y-auto pr-1">
                          {subs.map((sub) => (
                            <div key={sub.id} className="flex items-center justify-between py-2 px-3 bg-card rounded-md border border-border">
                              <div className="flex items-center gap-2.5">
                                <div className="w-7 h-7 rounded-full bg-primary flex items-center justify-center text-xs text-primary-foreground font-bold shrink-0">
                                  {sub.roll_no.slice(-2).toUpperCase()}
                                </div>
                                <div>
                                  <p className="text-sm font-semibold">{sub.roll_no}</p>
                                  <p className="text-xs text-muted-foreground font-mono">{sub.file_path.split("/").pop()}</p>
                                </div>
                              </div>
                              <div className="flex items-center gap-2">
                                <span className={`text-xs px-2 py-0.5 rounded-full font-medium ${sub.status === "submitted" ? "bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-400" : "bg-orange-100 text-orange-700"}`}>
                                  {sub.status}
                                </span>
                                <span className="flex items-center gap-1 text-xs text-muted-foreground">
                                  <Timer className="w-3 h-3" />
                                  {new Date(sub.submitted_at).toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" })}
                                </span>
                                <a
                                  href={submissionDownloadUrl(sub.id)}
                                  download
                                  className="ml-1 p-1.5 rounded hover:bg-secondary text-muted-foreground hover:text-foreground transition-colors"
                                  title={`Download ${sub.file_path.split("/").pop()}`}
                                >
                                  <Download className="w-3.5 h-3.5" />
                                </a>
                              </div>
                            </div>
                          ))}
                        </div>
                      )}
                    </div>
                  </CardContent>
                </Card>
              );
            })}
          </div>
        )}
      </main>
    </div>
  );
};

export default SubjectDetail;
