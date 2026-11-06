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

- **Keyword matching can match names.** The keyword part of the score (10%) compares words from the
  job description with every word in the resume, including the candidate's name, company names and
  place names. For example, a candidate named "Riley Example" matches the keyword "example" in a job
  posted by "Example Payments Co.". The effect on the score is small, but a keyword can occasionally
  show as matched for this reason.
- **Rate limits are kept in memory.** Login, sign-up and daily analysis counts reset when the app
  restarts, and each app instance counts separately. That's fine for a single instance; running
  several would need a shared store such as the database.
- **Behind a proxy, the trusted proxy range must be set.** Client IPs come from `X-Forwarded-For` only
  when the request arrives from an address in `server.tomcat.remoteip.internal-proxies` (loopback by
  default). On Render, set the `SERVER_TOMCAT_REMOTEIP_INTERNALPROXIES` environment variable to the
  range Render's proxy connects from; otherwise every user shares the proxy's IP for the per-IP limits.
