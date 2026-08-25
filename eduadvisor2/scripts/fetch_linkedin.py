#!/usr/bin/env python3
"""
Fetches a LinkedIn profile using linkedin-api (unofficial, free).
Credentials read from env vars LI_EMAIL and LI_PASSWORD.
Usage:  python fetch_linkedin.py <linkedin_profile_url>
Output: JSON to stdout; errors as {"error": "..."}
"""
import sys, json, os, re, logging

# Suppress all library chatter
logging.disable(logging.CRITICAL)
for name in logging.root.manager.loggerDict:
    logging.getLogger(name).setLevel(logging.CRITICAL)

def safe(v, default=""):
    if v is None:
        return default
    return str(v).strip()

def extract_public_id(url):
    url = url.strip().rstrip("/")
    m = re.search(r"/in/([^/?#]+)", url)
    return m.group(1) if m else url.split("/")[-1]

def main():
    if len(sys.argv) < 2:
        print(json.dumps({"error": "No LinkedIn URL provided"}))
        sys.exit(1)

    li_url   = sys.argv[1]
    email    = os.environ.get("LI_EMAIL", "")
    password = os.environ.get("LI_PASSWORD", "")

    if not email or not password:
        print(json.dumps({"error": "LI_EMAIL / LI_PASSWORD not set"}))
        sys.exit(1)

    public_id = extract_public_id(li_url)

    try:
        from linkedin_api import Linkedin
        api = Linkedin(email, password)
    except Exception as e:
        print(json.dumps({"error": "Login failed: " + str(e)}))
        sys.exit(1)

    # ── Fetch profile ──────────────────────────────────────────────────────
    try:
        p = api.get_profile(public_id)
    except Exception as e:
        print(json.dumps({"error": "Profile fetch failed: " + str(e)}))
        sys.exit(1)

    if not p or not isinstance(p, dict):
        print(json.dumps({"error": "Empty profile response"}))
        sys.exit(1)

    # ── Skills (also call get_profile_skills for complete list) ───────────
    skills = []
    seen_skills = set()
    def add_skill(s):
        n = ""
        if isinstance(s, dict):
            n = safe(s.get("name") or s.get("localizedName") or "")
        elif isinstance(s, str):
            n = s.strip()
        if n and n.lower() not in seen_skills:
            seen_skills.add(n.lower())
            skills.append(n)

    for s in p.get("skills", []):
        add_skill(s)

    try:
        extra_skills = api.get_profile_skills(public_id)
        for s in (extra_skills or []):
            add_skill(s)
    except Exception:
        pass

    # ── Projects (Accomplishments section) ────────────────────────────────
    projects = []
    for proj in p.get("projects", []):
        t = safe(proj.get("title"))
        d = safe(proj.get("description"))
        if t:
            projects.append({"title": t, "description": d})

    # ── Certifications ────────────────────────────────────────────────────
    certs = []
    for c in p.get("certifications", []):
        name = safe(c.get("name"))
        auth = c.get("authority", {})
        auth_name = safe(auth.get("name") if isinstance(auth, dict) else auth)
        starts = c.get("timePeriod", {}).get("startDate", {})
        year   = starts.get("year", "") if isinstance(starts, dict) else ""
        if name:
            certs.append({"name": name, "authority": auth_name, "starts_at": {"year": str(year)}})

    # ── Publications ──────────────────────────────────────────────────────
    publications = []
    for pub in p.get("publications", []):
        name = safe(pub.get("name"))
        pub_name = safe(pub.get("publisher"))
        if name:
            publications.append({"name": name, "publisher": pub_name})

    # ── Courses ───────────────────────────────────────────────────────────
    courses = []
    for c in p.get("courses", []):
        n = safe(c.get("name") if isinstance(c, dict) else c)
        if n:
            courses.append({"name": n})

    # ── Honors / Awards ───────────────────────────────────────────────────
    honors = []
    for h in p.get("honors", []):
        n = safe(h.get("title") if isinstance(h, dict) else h)
        if n:
            honors.append({"title": n})

    # ── Experience ────────────────────────────────────────────────────────
    experiences = []
    for e in p.get("experience", []):
        title   = safe(e.get("title"))
        company = safe(e.get("companyName"))
        if title:
            experiences.append({"title": title, "company": company})

    # ── Education ─────────────────────────────────────────────────────────
    education = []
    for e in p.get("education", []):
        deg    = safe(e.get("degreeName"))
        school = safe(e.get("schoolName"))
        if school:
            education.append({"degree_name": deg, "school": school})

    # ── Activities / Posts ────────────────────────────────────────────────
    activities = []
    try:
        updates = api.get_profile_updates(public_id, limit=20)
        for u in (updates or []):
            title  = ""
            status = "Posted"
            # Try to extract text from the nested structure
            try:
                comm = u.get("commentary", {})
                if isinstance(comm, dict):
                    text_obj = comm.get("text", {})
                    if isinstance(text_obj, dict):
                        title = safe(text_obj.get("text", ""))
                    elif isinstance(text_obj, str):
                        title = text_obj.strip()
                elif isinstance(comm, str):
                    title = comm.strip()
            except Exception:
                pass
            if not title:
                # fallback: try actor's activity
                try:
                    title = safe(u.get("title", u.get("text", "")))
                except Exception:
                    pass
            activities.append({"title": title[:200], "activity_status": status})
    except Exception:
        pass

    # ── Connections ───────────────────────────────────────────────────────
    connections = 0
    for key in ("connections", "connectionsCount", "networkDistance"):
        try:
            v = p.get(key, 0)
            if v and str(v).isdigit():
                connections = int(v)
                break
        except Exception:
            pass

    result = {
        "full_name":                   (safe(p.get("firstName")) + " " + safe(p.get("lastName"))).strip(),
        "headline":                    safe(p.get("headline")),
        "summary":                     safe(p.get("summary")),
        "connections":                 connections,
        "accomplishment_projects":     projects,
        "certifications":              certs,
        "accomplishment_publications": publications,
        "accomplishment_courses":      courses,
        "honors":                      honors,
        "skills":                      skills,
        "experiences":                 experiences,
        "education":                   education,
        "activities":                  activities,
    }

    print(json.dumps(result, ensure_ascii=False))

if __name__ == "__main__":
    main()
