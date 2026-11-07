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

## Deploying to Render (free plan) with Neon

Create a **Web Service** from this repository with the **Docker** runtime (the `Dockerfile` builds
the frontend and the API into one image and runs the `prod` profile).

**Health check path:** `/actuator/health/liveness`
(it doesn't touch the database, so a sleeping Neon database isn't woken every few seconds, and a slow
database wake-up doesn't make Render restart the app; `/actuator/health` still includes the database).

**Environment variables:**

| Variable | What to put in it |
|---|---|
| `SPRING_DATASOURCE_URL` | Neon's **direct** (not "pooled") connection as a JDBC URL: `jdbc:postgresql://<endpoint-host>/<database>?sslmode=require` |
| `SPRING_DATASOURCE_USERNAME` | The Neon role (database user) |
| `SPRING_DATASOURCE_PASSWORD` | That role's password |
| `APP_JWT_SECRET` | A long random string, at least 32 characters (e.g. `openssl rand -hex 32`). Changing it logs everyone out. |
| `SWAGGER_ENABLED` | Optional. `true` turns Swagger UI and `/v3/api-docs` on; leave unset in production. |

Don't set `PORT` (Render sets it; the app listens on whatever it says) or `SPRING_PROFILES_ACTIVE`
(the image already uses `prod`). Use Neon's direct host rather than the pooled one: Flyway migrations
need a regular session, and the app only opens up to 5 connections anyway.

Expect a slow first request after the free instance has been idle for 15 minutes: the instance
wakes up and Java starts on a small slice of CPU (about 75 seconds when measured locally at 0.1 CPU),
and Neon may also need a moment to wake.

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
- **Client IPs on Render depend on Render's proxy setup.** Render doesn't document its headers. Based
  on Cloudflare's documentation (it appends the address that connected to it) and observed Render
  headers, the app reads `X-Forwarded-For` only from Render's private network and Cloudflare's
  published ranges, and skips at most two proxy hops from the right, so a client can't choose their
  own IP. If Render changes its proxy chain or Cloudflare adds ranges, the per-IP limits could end up
  shared by many users (too strict) until the lists in `application-prod.properties` are updated;
  the log line starting "Client IP:" after the first request shows whether the header looks as expected.
  Several people behind one network address (an office, a mobile carrier) also share one limit.
