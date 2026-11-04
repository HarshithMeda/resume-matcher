# Resume Matcher

A Spring Boot API that compares a resume (PDF) with a job description and explains the match score.

## Known limitations

- **Keyword matching can match names.** The keyword part of the score (10%) compares words from the
  job description with every word in the resume, including the candidate's name, company names and
  place names. For example, a candidate named "Riley Example" matches the keyword "example" in a job
  posted by "Example Payments Co.". The effect on the score is small, but a keyword can occasionally
  show as matched for this reason.
