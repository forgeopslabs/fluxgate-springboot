# SonarQube Code Coverage Setup

This document explains how to configure SonarQube with JaCoCo code coverage for the FluxGate Spring Boot Starter project.

## Configuration Overview

The project is now configured with:

- **JaCoCo** for code coverage collection
- **SonarQube Maven Plugin** for static code analysis
- **GitHub Actions workflow** for automated analysis

## Required Setup

### 1. SonarCloud Account Setup

1. Go to [SonarCloud.io](https://sonarcloud.io)
2. Sign in with your GitHub account
3. Import your repository (`keaz/fluxgate-springboot`)
4. Note your organization key (should be `keaz`)

### 2. Generate SonarQube Token

1. In SonarCloud, go to **My Account** → **Security**
2. Generate a new token with a descriptive name (e.g., "GitHub Actions")
3. Copy the token value

### 3. Add GitHub Repository Secret

1. Go to your GitHub repository settings
2. Navigate to **Secrets and variables** → **Actions**
3. Add a new repository secret:
   - **Name**: `SONAR_TOKEN`
   - **Value**: Your SonarCloud token

## Usage

### Automatic Analysis

The SonarQube analysis runs automatically on:
- **Push to main branch**
- **Pull requests** (opened, synchronized, reopened)

### Manual Analysis

You can run SonarQube analysis locally:

```bash
# Run tests and generate coverage report
mvn clean test

# Run SonarQube analysis (requires SONAR_TOKEN environment variable)
mvn sonar:sonar -Dsonar.token=$SONAR_TOKEN
```

**Note**: The analysis uses `mvn clean test` instead of `mvn clean verify` to avoid triggering GPG signing which is only needed for releases.

### Coverage Reports

JaCoCo generates coverage reports in multiple formats:
- **HTML**: `target/site/jacoco/index.html`
- **XML**: `target/site/jacoco/jacoco.xml` (used by SonarQube)
- **CSV**: `target/site/jacoco/jacoco.csv`

## Configuration Details

### JaCoCo Configuration

- **Minimum coverage**: 80% line coverage required
- **Report generation**: Automatic during `test` phase
- **Integration**: XML reports sent to SonarQube

### SonarQube Properties

Key properties configured in `pom.xml`:
- `sonar.organization`: keaz
- `sonar.projectKey`: keaz_fluxgate-springboot
- `sonar.host.url`: https://sonarcloud.io
- `sonar.coverage.jacoco.xmlReportPaths`: target/site/jacoco/jacoco.xml

## Viewing Results

### SonarCloud Dashboard

After analysis completes:
1. Go to [SonarCloud.io](https://sonarcloud.io)
2. Navigate to your project
3. View metrics including:
   - Code coverage percentage
   - Bugs and vulnerabilities
   - Code smells
   - Security hotspots

### GitHub Integration

SonarCloud will:
- Comment on pull requests with analysis results
- Block PRs if quality gate fails (configurable)
- Show status checks in GitHub

## Customization

### Adjusting Coverage Thresholds

Edit the JaCoCo plugin configuration in `pom.xml`:

```xml
<limits>
    <limit>
        <counter>LINE</counter>
        <value>COVEREDRATIO</value>
        <minimum>0.80</minimum> <!-- Change this value -->
    </limit>
</limits>
```

### Excluding Files from Coverage

Add exclusions to JaCoCo configuration:

```xml
<configuration>
    <excludes>
        <exclude>**/dto/**</exclude>
        <exclude>**/config/**</exclude>
    </excludes>
</configuration>
```

### SonarQube Quality Gates

Configure quality gates in SonarCloud:
1. Go to **Quality Gates** in your project
2. Set custom conditions for:
   - Coverage percentage
   - Duplication ratio
   - Maintainability rating

## Troubleshooting

### Common Issues

1. **No coverage data**: Ensure tests are running and JaCoCo agent is attached
2. **SonarQube authentication failed**: Verify `SONAR_TOKEN` secret is set correctly
3. **Analysis not triggering**: Check workflow file syntax and triggers

### Local Development

Generate coverage report locally:
```bash
mvn clean test jacoco:report
open target/site/jacoco/index.html
```

### Debug Mode

Run with debug logging:
```bash
mvn sonar:sonar -Dsonar.verbose=true -X
```