<p align="center">
  <picture>
    <source media="(prefers-color-scheme: dark)" srcset="assets/logo-with-text-dark.svg">
    <source media="(prefers-color-scheme: light)" srcset="assets/logo-with-text-light.svg">
    <img src="assets/logo-with-text-light.svg" alt="Ollumi (a BookLore fork)" height="80" />
  </picture>
</p>

<h1 align="center">Ollumi</h1>

<p align="center"><strong>Your books deserve a home. This is it.</strong></p>

<p align="center">
Ollumi is a self-hosted app that brings your entire book collection under one roof.<br/>
Organize, read, annotate, sync across devices, and share, all without relying on third-party services.
</p>

<p align="center">
  <a href="LICENSE"><img src="https://img.shields.io/badge/license-AGPL--3.0-fab005?style=flat-square" alt="License" /></a>
  <a href="https://github.com/cdome/ollumi/stargazers"><img src="https://img.shields.io/github/stars/cdome/ollumi?style=flat-square&color=ffd43b" alt="Stars" /></a>
</p>

<p align="center">
  <a href="#-about-this-fork">🔱 About This Fork</a> ·
  <a href="#-performance">⚡ Performance</a> ·
  <a href="#-quick-start">🚀 Quick Start</a>
</p>

<p align="center">
  <img src="assets/demo.gif" alt="Ollumi Demo" width="800" />
</p>

---

## 🔱 About This Fork

Ollumi is a personal fork of [BookLore](https://github.com/booklore-app/booklore), a fantastic self-hosted book/comic/audiobook library manager. Everything you see below — the features, the UI, the overall design — is BookLore's work. This fork exists for a handful of changes that aren't upstream (yet, or at all):

- **Backend rewritten off Hibernate/JPA onto [jOOQ](https://www.jooq.org/)** for every read and write path. No ORM, no entity graph, no lazy-loading surprises — every query is explicit SQL built with jOOQ's typed DSL. See [⚡ Performance](#-performance) below for what that bought, and [`docs/architecture-backend.md`](docs/architecture-backend.md) for the full technical writeup.
- Assorted smaller changes (dependency bumps, test coverage, CI tweaks) made along the way to support the above.

If you just want BookLore, go use [the real thing](https://github.com/booklore-app/booklore) — it has an active community, a Discord server, hosted docs, and people who'll actually answer your issues. This fork is not a replacement for that project, has no community infrastructure of its own, and makes no compatibility promises. It's published in case the backend rewrite is useful or interesting to someone.

---

## ✨ Features

| | Feature | Description |
|:---:|:---|:---|
| 📚 | **Smart Shelves** | Custom and dynamic shelves that organize themselves with rule-based Magic Shelves, filters, and full-text search |
| 🔍 | **Automatic Metadata** | Covers, descriptions, reviews, and ratings pulled from Google Books, Open Library, and Amazon, all editable |
| 📖 | **Built-in Reader** | Open PDFs, EPUBs, and comics right in the browser with annotations, highlights, and reading progress |
| 🔄 | **Device Sync** | Connect your Kobo, use any OPDS-compatible app, or sync progress with KOReader. Your library follows you everywhere |
| 👥 | **Multi-User Ready** | Individual shelves, progress, and preferences per user with local or OIDC authentication |
| 📥 | **BookDrop** | Drop files into a watched folder and Ollumi detects, enriches, and queues them for import automatically |
| 📧 | **One-Click Sharing** | Send any book to a Kindle, an email address, or a friend instantly |

(All of the above is upstream BookLore functionality — see [their docs](https://booklore.org/docs/getting-started) for full usage details, OIDC setup, and upgrade guides. Most of it applies here unchanged.)

---

## ⚡ Performance

The headline change in this fork is moving the entire `booklore-api` backend off Hibernate/JPA onto jOOQ (see [About This Fork](#-about-this-fork)). The repo includes a Gatling load-test harness (`booklore-api/perf-tests/`) built to measure that migration's real impact, rather than guess at it.

**Setup**: 100 concurrent users (closed load model) against a seeded dataset of 10,000 books, 1,001 users, 1,354 shelves, and 50,000+ reading-progress rows, hitting a mixed, weighted traffic profile across library browsing, paged/filtered book lists, search, shelves, and reading stats — six endpoint groups in total, same dataset and load shape on both sides. Zero failed requests on either run.

| Metric | Before | After jOOQ | Change |
|---|---|---|---|
| Throughput | 28.8 req/s | 46.5 req/s | **+62%** |
| p50 latency | 723 ms | 13 ms | **~56× faster** |
| p95 latency | 4,243 ms | 403 ms | **~10× faster** |
| p99 latency | 5,829 ms | 421 ms | **~14× faster** |

### Hot paths (p95 latency)

| Endpoint | Before | After jOOQ | Change |
|---|---|---|---|
| `GET /api/v1/books` (unpaginated full dump) | 6,732 ms | 428 ms | ~16× |
| `GET /api/v1/app/books` (paged) | 3,563 ms | 22 ms | ~160× |
| `GET /api/v1/app/books` (filtered) | 3,302 ms | 39 ms | ~85× |
| `GET /api/v1/app/books/search` | 3,400 ms | 31 ms | ~110× |

The driver is straightforward: Hibernate was pulling these listings through entity-graph fetches designed for correctness and convenience, not for serving a filtered page of 50 books out of 10,000. jOOQ replaces that with exactly the SQL each endpoint actually needs.

> [!NOTE]
> This run was taken mid-migration — comparing an early checkpoint against a later one, both already partway off Hibernate — not a from-scratch-JPA vs. fully-Hibernate-free final comparison. The backend has since been migrated the rest of the way (no Hibernate dependency remains anywhere in the project at all — see `docs/architecture-backend.md`), but that later work was mostly about write paths and entity cleanup, not the hot read paths measured above, so these numbers are a reasonably honest picture of what end users actually feel. Full methodology, the seeded-dataset spec, and instructions to reproduce it against your own build are in `booklore-api/perf-tests/README.md`.

---

## 🚀 Quick Start

> [!TIP]
> Looking for OIDC setup, advanced config, or upgrade guides? See [BookLore's docs](https://booklore.org/docs/getting-started) — most of it applies here unchanged.

Ollumi has no published Docker image — build it from source. All you need is [Docker](https://docs.docker.com/get-docker/), [Docker Compose](https://docs.docker.com/compose/install/), and this repo cloned locally.

### Step 1: Clone and configure

```bash
git clone https://github.com/cdome/ollumi.git
cd ollumi
```

Create a `.env` file:

```ini
# Application
APP_USER_ID=1000
APP_GROUP_ID=1000
TZ=Etc/UTC

# Database
DATABASE_URL=jdbc:mariadb://mariadb:3306/booklore
DB_USER=booklore
DB_PASSWORD=ChangeMe_OllumiApp_2025!

# Storage: LOCAL (default) or NETWORK (disables file operations, see Network Storage section below)
DISK_TYPE=LOCAL

# MariaDB
DB_USER_ID=1000
DB_GROUP_ID=1000
MYSQL_ROOT_PASSWORD=ChangeMe_MariaDBRoot_2025!
MYSQL_DATABASE=booklore
```

### Step 2: Docker Compose

Create a `docker-compose.yml` that builds the image from the `Dockerfile` in this repo instead of pulling one:

```yaml
services:
  ollumi:
    build:
      context: .
      dockerfile: Dockerfile
    container_name: ollumi
    environment:
      - USER_ID=${APP_USER_ID}
      - GROUP_ID=${APP_GROUP_ID}
      - TZ=${TZ}
      - DATABASE_URL=${DATABASE_URL}
      - DATABASE_USERNAME=${DB_USER}
      - DATABASE_PASSWORD=${DB_PASSWORD}
      - DISK_TYPE=${DISK_TYPE}
    depends_on:
      mariadb:
        condition: service_healthy
    ports:
      - "6060:6060"
    volumes:
      - ./data:/app/data
      - ./books:/books
      - ./bookdrop:/bookdrop
    healthcheck:
      test: wget -q -O - http://localhost:6060/api/v1/healthcheck
      interval: 60s
      retries: 5
      start_period: 60s
      timeout: 10s
    restart: unless-stopped

  mariadb:
    image: lscr.io/linuxserver/mariadb:11.4.5
    container_name: mariadb
    environment:
      - PUID=${DB_USER_ID}
      - PGID=${DB_GROUP_ID}
      - TZ=${TZ}
      - MYSQL_ROOT_PASSWORD=${MYSQL_ROOT_PASSWORD}
      - MYSQL_DATABASE=${MYSQL_DATABASE}
      - MYSQL_USER=${DB_USER}
      - MYSQL_PASSWORD=${DB_PASSWORD}
    volumes:
      - ./mariadb/config:/config
    restart: unless-stopped
    healthcheck:
      test: [ "CMD", "mariadb-admin", "ping", "-h", "localhost" ]
      interval: 5s
      timeout: 5s
      retries: 10
```

### Step 3: Build and launch

```bash
docker compose up -d --build
```

The first build takes a while (Angular build + Gradle build, no cached layers yet). Once it's up, open **http://localhost:6060**, create your admin account, and start building your library.

---

## ⚠️ Network Storage (NAS / NFS / SMB / CIFS)

> [!CAUTION]
> Ollumi's file operations (metadata writing, file renaming, file organization) are built for **local file systems only**. Network-attached storage (NAS, NFS, SMB/CIFS mounts, cloud-backed FUSE, etc.) is **unsupported and untested**. Mount options, network latency, caching, and filesystem semantics are all outside Ollumi's control and can cause silent file corruption, incomplete writes, missing files, and other unpredictable behavior.

If your book files live on network storage, set `DISK_TYPE=NETWORK` in your `.env` file. This puts Ollumi into **network storage mode**, which disables all file write and reorganization features. Metadata is stored in the database only and your files are never modified. This is the only supported configuration for network storage.

---

## 📥 BookDrop: Zero-Effort Import

Drop book files into a folder. Ollumi picks them up, pulls metadata, and queues everything for your review.

```mermaid
graph LR
    A[📁 Drop Files] --> B[🔍 Auto-Detect]
    B --> C[📊 Extract Metadata]
    C --> D[✅ Review & Import]
```

| Step | What Happens |
|:---|:---|
| 1. **Watch** | Ollumi monitors the BookDrop folder around the clock |
| 2. **Detect** | New files are picked up and parsed automatically |
| 3. **Enrich** | Metadata is fetched from Google Books and Open Library |
| 4. **Import** | You review, tweak if needed, and add to your library |

Mount the volume in `docker-compose.yml`:

```yaml
volumes:
  - ./bookdrop:/bookdrop
```

---

## 🛠️ Contributing / Issues

This is a personal fork with no dedicated community infrastructure — issues and PRs against this repo are welcome but handled on a best-effort basis. If your interest is in BookLore itself (not the jOOQ rewrite), please go to [the upstream project](https://github.com/booklore-app/booklore) instead — that's where the active community, Discord, and maintainers are.

---

## 📊 Project Analytics

### ⭐ Star History

<a href="https://www.star-history.com/#cdome/ollumi&type=date&legend=top-left">
 <picture>
   <source media="(prefers-color-scheme: dark)" srcset="https://api.star-history.com/svg?repos=cdome/ollumi&type=date&theme=dark&legend=top-left" />
   <source media="(prefers-color-scheme: light)" srcset="https://api.star-history.com/svg?repos=cdome/ollumi&type=date&legend=top-left" />
   <img alt="Star History Chart" src="https://api.star-history.com/svg?repos=cdome/ollumi&type=date&legend=top-left" width="600" />
 </picture>
</a>

---

## ⚠️ Note to Integrators

While Ollumi is open source and its API is accessible, it is not designed or maintained as a stable integration point. Endpoints are undocumented, unversioned, and may change or break at any time without notice. No compatibility guarantees or support are provided for third-party use.

<div align="center">

## ⚖️ License

**GNU Affero General Public License v3.0**

Copyright 2024–2026 BookLore

Ollumi is a fork of BookLore and remains under the same license; the original project's copyright notice above is preserved as required by the AGPL. Changes made in this fork are documented in the commit history.

[![License: AGPL v3](https://img.shields.io/badge/License-AGPL_v3-blue.svg?style=for-the-badge)](https://www.gnu.org/licenses/agpl-3.0.html)

</div>
