# Resume Matcher

A Spring Boot API that compares a resume (PDF) with a job description and explains the match score.

## Running locally

- **Everything in Docker:** `docker compose up --build`, then open http://localhost:8080.
  Needs a `.env` file (copy `.env.example` and fill in random values).
- **While developing:** run the API with `./mvnw spring-boot:run` (uses `local.properties`) and the
  frontend with `cd frontend && npm install && npm run dev`, then open http://localhost:5173.
  The Vite dev server forwards `/api` to the API on port 8080.
- **Tests:** `./mvnw verify` (needs Docker for the PostgreSQL test containers) and
  `cd frontend && npm test`.

## Known limitations

- **Keyword matching can match names.** Words the job posting only ever writes Capitalized (company
  names like "Example Health Partners", or verbs that open bullets) are skipped as keywords. But a
  resume is compared word by word, including the candidate's name and past employers, so a keyword
  can occasionally show as matched because of a name. It only affects the keyword part (10%).
- **Either-or is read from the wording only.** Skills joined by "or" or a slash ("Power BI or
  Tableau", "Java/Kotlin", "AWS, Azure or GCP") count as one group that any of them satisfies. Lists
  joined by "and" don't, and a comma list only joins the group while its items are the same kind of
  skill ("Java, SQL, Docker or Kubernetes" groups just Docker and Kubernetes). Alternatives written
  in other ways ("one of: …", separate bullets) are still counted as separate skills, and a slash
  always means "or" (so "Agile/Scrum" is satisfied by either one).
- **Skills are found by wording.** Each skill has a list of names and common phrasings (for example
  "UAT", "user acceptance testing" and "UAT sessions"; "stakeholder engagement" and "managed
  stakeholders"). A resume that describes a skill in words that aren't on the list won't be credited
  for it. The lists are kept fairly strict on purpose, so phrases like "continuous improvement" or
  "functional requirements" don't turn every developer posting into a business-analysis one.
- **Word forms use simple rules.** Keywords are compared by a rule-based base form ("process",
  "processes" and "processing" are one keyword; so are "document", "documented" and "documentation").
  It doesn't know irregular forms ("ran" vs "run") or every derived word ("automation" and "automate"
  stay different), and everyday verbs and filler words ("manage", "online", "team") are ignored.
- **Rate limits are kept in memory.** Login, sign-up and daily analysis counts reset when the app
  restarts, and each app instance counts separately. That's fine for a single instance; running
  several would need a shared store such as the database.
- **Behind a proxy, the trusted proxy range must be set.** Client IPs come from `X-Forwarded-For` only
  when the request arrives from an address in `server.tomcat.remoteip.internal-proxies` (loopback by
  default). On Render, set the `SERVER_TOMCAT_REMOTEIP_INTERNALPROXIES` environment variable to the
  range Render's proxy connects from; otherwise every user shares the proxy's IP for the per-IP limits.
