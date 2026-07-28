# TuniHire

A full-stack job recruitment platform built with **Java Spring Boot**, **MySQL**, and a modern **HTML/CSS/JavaScript** frontend.

## Features

- **Candidate accounts** — register, log in, apply to jobs, track applications
- **Company accounts** — register, log in, post jobs, review applicants, contact candidates
- Browse and search job openings
- Filter jobs by type (full-time, remote, internship, contract, etc.)
- View full job details
- Live dashboard stats

## Requirements

- Java 17 or higher
- Maven 3.8+
- MySQL 8.0+ (optional — built-in database works without it)

## Setup

### 1. Create the MySQL database (only if using MySQL profile)

Open MySQL and run:

```sql
source database/schema.sql
```

Or copy and paste the contents of `database/schema.sql` into MySQL Workbench.

### 2. Configure database (optional)

By default the app uses the configured **MySQL database**.

If you want to use the temporary built-in database instead, edit `src/main/resources/application.properties`:

```properties
spring.profiles.active=dev
```

For MySQL, run `database/schema.sql` and set `DB_USERNAME` and `DB_PASSWORD` in your environment when they differ from `root` and an empty password.

### 3. Run the application

**Option A — Double-click (Windows, no IntelliJ)**

Double-click **`start.bat`** in the project folder (needs Java 17 + Maven installed).

**Option B — IntelliJ IDEA**

1. Open the project folder
2. Right-click `pom.xml` → **Add as Maven Project**
3. Wait for dependencies to download
4. Run `RecruitmentSystemApplication.java`

**Option C — Command line**

```bash
mvn spring-boot:run
```

### 4. Open the website

Go to: **http://localhost:8082**

Do **not** open `web/index.html` directly in the browser — the server must be running.

## Deployment (Docker)

```bash
docker build -t tunihire .
docker run -p 8082:8082 tunihire
```

Then open **http://localhost:8082** (or your server’s public IP).

## Company vs candidate experience

- **Candidates** see job listings, companies, search, and apply.
- **Companies** go straight to **Find candidates** — suggested people to hire (your applicants + talent on the platform). They do **not** see the public job board or company directory.

## API Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/auth/register/candidate` | Create candidate account |
| POST | `/api/auth/register/company` | Create company account |
| POST | `/api/auth/login` | Log in |
| POST | `/api/auth/logout` | Log out |
| GET | `/api/auth/me` | Current logged-in user |
| GET | `/api/jobs` | List jobs (supports `keyword`, `location`, `type`) |
| GET | `/api/jobs/{id}` | Get job details |
| POST | `/api/jobs` | Post a new job (company only) |
| GET | `/api/companies` | List companies with job counts |
| POST | `/api/applications` | Submit application (candidate only) |
| GET | `/api/applications/me` | My applications (candidate only) |
| GET | `/api/applications/manage` | Manage applicants (company only) |
| PATCH | `/api/applications/{id}/status` | Update application status (company only) |
| POST | `/api/applications/{id}/contact` | Send message to candidate (company only) |
| GET | `/api/stats` | Dashboard statistics |

## Project Structure

```
Recruitment_System/
├── database/schema.sql          # MySQL database schema + sample data
├── pom.xml                      # Maven / Spring Boot config
├── src/main/java/com/recruitment/
│   ├── RecruitmentSystemApplication.java
│   ├── controller/              # REST API endpoints
│   ├── service/                 # Business logic
│   ├── dto/                     # Data transfer objects
│   ├── config/                  # Web configuration
│   └── exception/               # Error handling
├── src/main/resources/
│   └── application.properties   # Database & server settings
└── web/                         # Frontend (HTML, CSS, JS)
```

## Verify everything works

1. Click **Create account** → register as **Candidate** or **Company**
2. **Candidate**: browse jobs → Apply → check **My applications**
3. **Company**: **Post job** → **Applicants** → update status → **Contact** a candidate
4. Log out and log back in to confirm sessions work

```sql
SELECT * FROM applications;
SELECT * FROM jobs ORDER BY id DESC;
```

## Troubleshooting

| Problem | Solution                                                                                      |
|---------|-----------------------------------------------------------------------------------------------|
| **Cannot connect to server** | Run `start.bat` or `mvn spring-boot:run`, then open http://localhost:8082 (not the HTML file) |
| Jobs not loading | Wait for "TuniHire is RUNNING" in the console                                       |
| MySQL connection error | Switch to `spring.profiles.active=dev` in `application.properties`                            |
| Connection refused | Server is not running — start it in IntelliJ                                                  |
| Port 8080 in use | Change `server.port` in `application.properties`                                              |
| Maven errors | Ensure Java 17+ is installed                                                                  |
