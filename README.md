# Sahaara Connect

Sahaara Connect is a self-contained Java web application that connects
donors, NGOs/organizations, and healthcare facilities to manage needs,
donations, and patient records. It ships as a single Java source file
and uses only the JDK's built-in HTTP server (`com.sun.net.httpserver`)
— no external frameworks or dependencies required.

## Requirements

- JDK 11 or newer (tested on JDK 21)

## Project structure

```
sahaara-connect/
├── src/
│   └── SahaaraConnectApp.java   # entire application (server, routes, models, views)
├── .gitignore
└── README.md
```

## Run locally

```bash
# 1. Compile
cd src
javac SahaaraConnectApp.java

# 2. Run
java SahaaraConnectApp
```

The app starts an HTTP server on port **8085**. Open your browser at:

```
http://localhost:8085
```

On first run, the app seeds some sample organizations, needs, and
healthcare facilities and persists state to a `sahaara_connect.data`
file (created next to wherever you run the app from). This file is
Java-serialized runtime data and is intentionally excluded from
version control via `.gitignore` — delete it any time to reset to a
fresh seeded state.

## Features

- Organization directory (create/delete)
- Needs & fundraising campaigns with donation tracking
- Donation checkout flow with a receipt page and tax-benefit info
- Healthcare facility directory
- Patient records tied to facilities and organizations
- Server-rendered HTML/CSS/JS, no build tooling required

## Notes for deployment

- Change the port by editing the `InetSocketAddress` in `main()`.
- The app stores state in-process and persists it to disk on each
  write — this is fine for small deployments/demos but is not a
  substitute for a real database at scale.
