const _loginForm = document.getElementById("loginForm");
if (_loginForm) {
  _loginForm.addEventListener("submit", function (event) {
    event.preventDefault();

    const studentId = document.getElementById("studentId").value.trim();
    const password = document.getElementById("password").value.trim();
    const message = document.getElementById("message");

    if (!studentId || !password) {
      message.style.color = "#d93025";
      message.textContent = "Please enter both Student ID and password.";
      return;
    }

    if (studentId === "S12345" && password === "student2026") {
      message.style.color = "#0f8a2f";
      message.textContent = "Login successful! Redirecting...";
      setTimeout(() => {
        window.location.href = "dashboard.html";
      }, 1200);
    } else {
      message.style.color = "#d93025";
      message.textContent = "Invalid Student ID or password.";
    }
  });
}
'use strict';

// ── Tab Navigation ──────────────────────────────────────────────────────────
function switchTab(name) {
  document.querySelectorAll('.tab').forEach(t => t.classList.remove('active'));
  document.querySelectorAll('.nav-btn').forEach(b => b.classList.remove('active'));
  document.getElementById('tab-' + name).classList.add('active');
  document.querySelector(`[data-tab="${name}"]`).classList.add('active');
}

document.querySelectorAll('.nav-btn').forEach(btn => {
  btn.addEventListener('click', () => switchTab(btn.dataset.tab));
});

// ── Course Rows ─────────────────────────────────────────────────────────────
let courseCount = 0;

function letterGradeClass(grade) {
  const g = grade.trim().toUpperCase();
  if (['A+','A','A-','B+','B','B-'].includes(g)) return 'pass';
  if (['C+','C'].includes(g)) return 'borderline';
  return 'fail';
}

function addCourseRow() {
  courseCount++;
  const list = document.getElementById('courseList');
  const row = document.createElement('div');
  row.className = 'course-row';
  row.dataset.id = courseCount;
  row.innerHTML = `
    <div class="field">
      ${courseCount === 1 ? '<label>Source Course Name</label>' : ''}
      <input type="text" placeholder="e.g. Introduction to Algorithms" class="src-name" />
    </div>
    <div class="field">
      ${courseCount === 1 ? '<label>Equivalent Course Here</label>' : ''}
      <input type="text" placeholder="e.g. CS201 — Data Structures" class="dest-name" />
    </div>
    <div class="field">
      ${courseCount === 1 ? '<label>Credits</label>' : ''}
      <input type="number" placeholder="3" min="1" max="12" class="credits-input" />
    </div>
    <div class="field">
      ${courseCount === 1 ? '<label>Grade</label>' : ''}
      <input type="text" placeholder="A / B+ / 85%" class="grade-input" maxlength="4" />
    </div>
    <div class="field">
      ${courseCount === 1 ? '<label>Status</label>' : ''}
      <span class="grade-badge" style="margin-top:9px">—</span>
    </div>
    <div class="field">
      <button class="btn-remove" title="Remove row">×</button>
    </div>`;

  // Grade → badge live update
  const gradeInput = row.querySelector('.grade-input');
  const badge = row.querySelector('.grade-badge');
  gradeInput.addEventListener('input', () => {
    const v = gradeInput.value.trim();
    if (!v) { badge.textContent = '—'; badge.className = 'grade-badge'; return; }
    badge.textContent = v.toUpperCase();
    badge.className = 'grade-badge ' + letterGradeClass(v);
  });

  // Credits → recalc summary
  row.querySelector('.credits-input').addEventListener('input', updateCreditSummary);

  // Remove
  row.querySelector('.btn-remove').addEventListener('click', () => {
    row.remove();
    updateCreditSummary();
  });

  list.appendChild(row);
  updateCreditSummary();
}

function updateCreditSummary() {
  const rows = document.querySelectorAll('.course-row');
  let total = 0;
  rows.forEach(r => {
    const v = parseFloat(r.querySelector('.credits-input').value);
    if (!isNaN(v)) total += v;
  });
  const summary = document.getElementById('creditSummary');
  summary.style.display = rows.length ? 'flex' : 'none';
  document.getElementById('totalCredits').textContent = total;
  document.getElementById('totalCourses').textContent = rows.length;
}

document.getElementById('addCourseBtn').addEventListener('click', addCourseRow);

// Start with one empty row
addCourseRow();

// ── Validation ──────────────────────────────────────────────────────────────
function validate() {
  let ok = true;

  function require(id, msg) {
    const el = document.getElementById(id);
    const existing = el.parentElement.querySelector('.field-error');
    if (existing) existing.remove();
    el.classList.remove('error');
    if (!el.value.trim()) {
      el.classList.add('error');
      const err = document.createElement('span');
      err.className = 'field-error';
      err.textContent = msg || 'This field is required.';
      el.after(err);
      ok = false;
    }
  }

  function requireEmail(id) {
    const el = document.getElementById(id);
    const existing = el.parentElement.querySelector('.field-error');
    if (existing) existing.remove();
    el.classList.remove('error');
    if (!el.value.trim() || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(el.value)) {
      el.classList.add('error');
      const err = document.createElement('span');
      err.className = 'field-error';
      err.textContent = 'Enter a valid email address.';
      el.after(err);
      ok = false;
    }
  }

  require('studentName', 'Enter your full name.');
  require('studentId', 'Enter your student ID.');
  requireEmail('studentEmail');
  require('currentProgram', 'Select your current program.');
  require('sourceInstitution', 'Enter the source institution name.');
  require('targetDepartment', 'Select a target department.');
  require('targetProgram', 'Select a target program.');

  const rows = document.querySelectorAll('.course-row');
  if (rows.length === 0) {
    alert('Please add at least one course to transfer.');
    ok = false;
  }

  rows.forEach(row => {
    const srcName = row.querySelector('.src-name');
    if (!srcName.value.trim()) {
      srcName.classList.add('error');
      ok = false;
    }
  });

  if (!document.getElementById('declaration').checked) {
    alert('Please confirm the declaration before submitting.');
    ok = false;
  }

  return ok;
}

// ── Build Request Object ─────────────────────────────────────────────────────
function buildRequest(isDraft) {
  const courses = [];
  document.querySelectorAll('.course-row').forEach(row => {
    courses.push({
      source: row.querySelector('.src-name').value.trim(),
      dest: row.querySelector('.dest-name').value.trim(),
      credits: parseFloat(row.querySelector('.credits-input').value) || 0,
      grade: row.querySelector('.grade-input').value.trim(),
    });
  });

  return {
    ref: 'CT-' + Date.now().toString(36).toUpperCase().slice(-6),
    studentName: document.getElementById('studentName').value.trim(),
    studentId: document.getElementById('studentId').value.trim(),
    email: document.getElementById('studentEmail').value.trim(),
    currentProgram: document.getElementById('currentProgram').value,
    gpa: document.getElementById('gpa').value,
    sourceInstitution: document.getElementById('sourceInstitution').value.trim(),
    sourceCountry: document.getElementById('sourceCountry').value,
    targetDepartment: document.getElementById('targetDepartment').value,
    targetProgram: document.getElementById('targetProgram').value,
    entryLevel: document.getElementById('entryLevel').value,
    transferSemester: document.getElementById('transferSemester').value,
    courses,
    notes: document.getElementById('notes').value.trim(),
    submittedAt: new Date().toISOString(),
    status: isDraft ? 'draft' : 'pending',
  };
}

// ── Save & Load from localStorage ───────────────────────────────────────────
function loadRequests() {
  try { return JSON.parse(localStorage.getItem('creditRequests') || '[]'); } catch { return []; }
}

function saveRequests(list) {
  localStorage.setItem('creditRequests', JSON.stringify(list));
}

// ── Render My Requests ───────────────────────────────────────────────────────
const statusLabels = {
  pending: { label: 'Pending Review', icon: '⏳', cls: 'pending' },
  review:  { label: 'Under Review',   icon: '🔍', cls: 'review'  },
  approved:{ label: 'Approved',       icon: '✅', cls: 'approved' },
  rejected:{ label: 'Rejected',       icon: '❌', cls: 'rejected' },
  draft:   { label: 'Draft',          icon: '✏️', cls: 'draft'   },
};

function renderRequests() {
  const list = loadRequests();
  const noReq = document.getElementById('noRequests');
  const container = document.getElementById('requestsContainer');

  if (list.length === 0) {
    noReq.style.display = 'block';
    container.style.display = 'none';
    return;
  }

  noReq.style.display = 'none';
  container.style.display = 'grid';
  container.innerHTML = '';

  [...list].reverse().forEach(req => {
    const st = statusLabels[req.status] || statusLabels.pending;
    const totalCredits = req.courses.reduce((s, c) => s + (c.credits || 0), 0);
    const date = new Date(req.submittedAt).toLocaleDateString('en-US', { year:'numeric', month:'short', day:'numeric' });

    const card = document.createElement('div');
    card.className = 'request-card';
    card.innerHTML = `
      <div class="req-card-header">
        <div>
          <h4>${escHtml(req.studentName)}</h4>
          <small>${escHtml(req.studentId)} &bull; ${date}</small>
        </div>
        <span class="status-chip ${st.cls}">${st.icon} ${st.label}</span>
      </div>
      <div class="req-meta">
        <span><strong>From:</strong> ${escHtml(req.sourceInstitution || '—')}</span>
        <span><strong>To:</strong> ${escHtml(req.targetProgram || '—')}</span>
        <span><strong>Semester:</strong> ${escHtml(req.transferSemester || '—')}</span>
        <span><strong>Total Credits:</strong> ${totalCredits}</span>
      </div>
      <div class="req-courses">
        ${req.courses.slice(0, 4).map(c => `<span class="course-tag">${escHtml(c.source || 'Course')}</span>`).join('')}
        ${req.courses.length > 4 ? `<span class="course-tag">+${req.courses.length - 4} more</span>` : ''}
      </div>
      <div class="req-card-footer">
        <span class="ref-tag">${escHtml(req.ref)}</span>
        ${req.status === 'draft'
          ? `<button class="btn-primary" style="padding:6px 14px;font-size:13px" onclick="loadDraft('${req.ref}')">Edit Draft</button>`
          : `<button class="btn-secondary" style="padding:6px 14px;font-size:13px" onclick="deleteRequest('${req.ref}')">Remove</button>`
        }
      </div>`;
    container.appendChild(card);
  });
}

// ── Delete Request ───────────────────────────────────────────────────────────
window.deleteRequest = function(ref) {
  if (!confirm('Remove this request from your list?')) return;
  const list = loadRequests().filter(r => r.ref !== ref);
  saveRequests(list);
  renderRequests();
};

// ── Load Draft ───────────────────────────────────────────────────────────────
window.loadDraft = function(ref) {
  const req = loadRequests().find(r => r.ref === ref);
  if (!req) return;

  document.getElementById('studentName').value = req.studentName || '';
  document.getElementById('studentId').value = req.studentId || '';
  document.getElementById('studentEmail').value = req.email || '';
  document.getElementById('currentProgram').value = req.currentProgram || '';
  document.getElementById('gpa').value = req.gpa || '';
  document.getElementById('sourceInstitution').value = req.sourceInstitution || '';
  document.getElementById('sourceCountry').value = req.sourceCountry || '';
  document.getElementById('targetDepartment').value = req.targetDepartment || '';
  document.getElementById('targetProgram').value = req.targetProgram || '';
  document.getElementById('entryLevel').value = req.entryLevel || '';
  document.getElementById('transferSemester').value = req.transferSemester || '';
  document.getElementById('notes').value = req.notes || '';

  const list = document.getElementById('courseList');
  list.innerHTML = '';
  courseCount = 0;
  req.courses.forEach(() => addCourseRow());
  document.querySelectorAll('.course-row').forEach((row, i) => {
    const c = req.courses[i];
    if (!c) return;
    row.querySelector('.src-name').value = c.source || '';
    row.querySelector('.dest-name').value = c.dest || '';
    row.querySelector('.credits-input').value = c.credits || '';
    row.querySelector('.grade-input').value = c.grade || '';
    row.querySelector('.grade-input').dispatchEvent(new Event('input'));
  });
  updateCreditSummary();

  switchTab('transfer');
};

// ── Submit ───────────────────────────────────────────────────────────────────
document.getElementById('submitBtn').addEventListener('click', () => {
  if (!validate()) return;
  const req = buildRequest(false);
  const list = loadRequests();
  list.push(req);
  saveRequests(list);
  showModal(req.ref);
  resetForm();
});

// ── Save Draft ───────────────────────────────────────────────────────────────
document.getElementById('saveDraftBtn').addEventListener('click', () => {
  const req = buildRequest(true);
  const list = loadRequests();
  list.push(req);
  saveRequests(list);
  alert('Draft saved! You can find it under My Requests.');
});

// ── Reset Form ───────────────────────────────────────────────────────────────
function resetForm() {
  ['studentName','studentId','studentEmail','gpa','sourceInstitution',
   'sourceProgramStudied','notes'].forEach(id => {
    document.getElementById(id).value = '';
  });
  ['currentProgram','sourceCountry','gradingSystem','targetDepartment',
   'targetProgram','entryLevel','transferSemester'].forEach(id => {
    document.getElementById(id).value = '';
  });
  ['docTranscript','docSyllabus','docDegree','docAccred','declaration'].forEach(id => {
    document.getElementById(id).checked = false;
  });
  const list = document.getElementById('courseList');
  list.innerHTML = '';
  courseCount = 0;
  addCourseRow();
  updateCreditSummary();
}

// ── Modal ────────────────────────────────────────────────────────────────────
function showModal(ref) {
  document.getElementById('refNumber').textContent = ref;
  document.getElementById('modal').style.display = 'flex';
}

document.getElementById('modalClose').addEventListener('click', () => {
  document.getElementById('modal').style.display = 'none';
  renderRequests();
  switchTab('status');
});

document.getElementById('modal').addEventListener('click', e => {
  if (e.target === document.getElementById('modal')) {
    document.getElementById('modal').style.display = 'none';
  }
});

// ── Utilities ────────────────────────────────────────────────────────────────
function escHtml(str) {
  return String(str)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');
}

// ── Init ─────────────────────────────────────────────────────────────────────
renderRequests();

