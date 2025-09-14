# Publishing to Maven Central

This document describes how to set up the required secrets for publishing to Maven Central via GitHub Actions.

## Required Repository Secrets

To publish to Maven Central, you need to configure the following secrets in your GitHub repository:

### 1. OSSRH Credentials

- **`OSSRH_USERNAME`**: Your Sonatype OSSRH username
- **`OSSRH_TOKEN`**: Your Sonatype OSSRH token (not password)

### 2. GPG Signing Key

- **`GPG_PRIVATE_KEY`**: Your GPG private key in ASCII armored format
- **`GPG_PASSPHRASE`**: The passphrase for your GPG private key

## Setting Up Secrets

### 1. OSSRH Account Setup

1. Create an account at [Sonatype JIRA](https://issues.sonatype.org)
2. Create a ticket to request access to publish to your group ID (`io.github.keaz`)
3. Once approved, generate a user token at [Sonatype OSSRH](https://s01.oss.sonatype.org/)

### 2. GPG Key Setup

1. Generate a GPG key pair:
   ```bash
   gpg --gen-key
   ```

2. Export your private key in ASCII armored format:
   ```bash
   gpg --armor --export-secret-keys YOUR_KEY_ID
   ```

3. Copy the entire output (including `-----BEGIN PGP PRIVATE KEY BLOCK-----` and `-----END PGP PRIVATE KEY BLOCK-----`)

### 3. Adding Secrets to GitHub

1. Go to your repository settings
2. Navigate to "Secrets and variables" → "Actions"
3. Add the following repository secrets:
   - `OSSRH_USERNAME`: Your Sonatype username
   - `OSSRH_TOKEN`: Your Sonatype token
   - `GPG_PRIVATE_KEY`: Your GPG private key (ASCII armored)
   - `GPG_PASSPHRASE`: Your GPG key passphrase

## Publishing Process

1. Create a GitHub release
2. The workflow will automatically:
   - Build the project
   - Sign the artifacts with GPG
   - Deploy to Maven Central
   - Release to production (if auto-release is enabled)

## Troubleshooting

### GPG Issues
- Ensure the GPG private key includes the full ASCII armored block
- Verify the passphrase is correct
- Make sure the key hasn't expired

### OSSRH Issues
- Verify your group ID is approved for publishing
- Check that your credentials are correct
- Ensure you're using a token, not a password

### Build Issues
- Check that all required plugins are properly configured in `pom.xml`
- Verify the project version follows semantic versioning
- Ensure all required metadata is present (name, description, URL, etc.)

## Security Notes

- Never commit GPG keys or passwords to the repository
- Use repository secrets for all sensitive information
- Regularly rotate your OSSRH tokens
- Consider using separate GPG keys for different projects