# Deployment guide

This project uses two services so each part runs in the right environment:

```text
Visitor browser
     │
     ▼
Vercel dashboard
     │  /api/report, /api/consumers, /api/history
     ▼
Render Java API
     │
     ▼
Sample CSV data and scoring engine
```

## 1. Deploy the Java API on Render

1. Create a Render account and choose **New → Blueprint**.
2. Connect the GitHub repository.
3. Set the repository root directory to `credit-bureau-simulator`.
4. Render will find `render.yaml` and the Dockerfile.
5. Create the service and wait until the health check at `/health` succeeds.
6. Copy the generated service URL. It looks like:

```text
https://credit-bureau-simulator-api.onrender.com
```

The server reads the platform-provided `PORT` variable automatically. It uses port `8080` only during local development.

## 2. Deploy the dashboard on Vercel

1. In Vercel, choose **Add New → Project** and import the same GitHub repository.
2. Set **Root Directory** to `credit-bureau-simulator`.
3. Set the framework preset to **Other**.
4. Leave the build command empty and keep the output directory as `.`.
5. Before deploying, add an environment variable:

```text
Name: BACKEND_URL
Value: https://credit-bureau-simulator-api.onrender.com
```

Replace the example URL with the Render URL from step 1. Add it to Production, Preview, and Development if you want all deployment types to work.

6. Click **Deploy**.

Vercel deploys `index.html` as the website. The files in `api/` are small serverless proxy functions; they forward the browser's API requests to the Java service. The frontend therefore keeps using `/api/report` and never needs to expose a backend URL in its source code.

## 3. Verify the live deployment

Open the Vercel URL and select a scenario. You should see a score, factor explanations, accounts, and applied rules.

You can also check these URLs directly:

```text
https://YOUR-RENDER-URL/health
https://YOUR-RENDER-URL/api/consumers
https://YOUR-VERCEL-URL/api/consumers
```

The first returns a small health response. The other two should return the consumer IDs.

## Local development

The usual local command still works:

```bash
mvn clean package
java -jar target/credit-bureau-simulator-1.0.0.jar serve --data ./sample-data
```

Open `http://localhost:8080`. No environment variable is needed locally.
