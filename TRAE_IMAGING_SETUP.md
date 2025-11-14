# CAST Imaging MCP Server Setup Guide for Trae AI

This guide documents the complete setup process for integrating CAST Imaging with Trae AI using the Model Context Protocol (MCP) server.

## Prerequisites

### Environment Readiness
1. **CAST Imaging Server**: Ensure CAST Imaging is installed and running locally
2. **Local Server**: CAST Imaging MCP server should be accessible at `http://localhost:8282`
3. **API Key**: Obtain a valid `imaging_api_key` for authentication
4. **Trae AI**: Have Trae AI IDE installed and configured

### System Dependencies

To ensure MCP servers can be launched properly, install the following:

#### Node.js (Required for npx)
1. **Download and Install**: Go to [Node.js website](https://nodejs.org/) and download Node.js 18 or higher
2. **Verify Installation**:
   ```powershell
   node -v
   npx -v
   ```
   Should output version numbers like:
   ```
   v18.19.0
   10.2.0
   ```

#### Python and uvx (Optional)
1. **Install Python**: Download Python 3.8+ from [Python official website](https://www.python.org/)
2. **Verify Python**:
   ```powershell
   python --version
   ```
3. **Install uv (includes uvx)**:
   ```powershell
   powershell -ExecutionPolicy ByPass -c "irm https://astral.sh/uv/install.ps1 | iex"
   ```
4. **Verify uvx**:
   ```powershell
   uvx --version
   ```

#### Docker (Optional)
- Required only for specific MCP servers like GitHub MCP
- Download from [Docker official website](https://www.docker.com/)
- Verify with: `docker --version`

## Step-by-Step Setup Process

### Step 1: Verify CAST Imaging Server
- Confirm CAST Imaging is running on `http://localhost:8282`
- Test the MCP endpoint at `http://localhost:8282/mcp/`
- Ensure you have a valid API key for authentication

### Step 2: Global MCP Configuration

1. **Locate Global MCP File**
   - Path: `C:\Users\[USERNAME]\AppData\Roaming\Trae\User\mcp.json`
   - This is the ONLY configuration file needed for MCP servers in Trae AI
   - No project-level `.vscode/mcp.json` is required

2. **Initial Configuration Attempts**
   - First attempt used `mcpServers` (incorrect)
   - Second attempt used `stdio` type (incorrect for HTTP server)
   - Third attempt used `npx` command (incorrect for HTTP server)

3. **Correct Global mcp.json Structure**:
   ```json
   {
     "servers": {
       "imaging": {
         "name": "CAST Imaging Server",
         "type": "http",
         "url": "http://localhost:8282/mcp/",
         "headers": {
           "imaging_api_key": "your_actual_api_key_here"
         }
       }
     }
   }
   ```

### Step 3: Key Configuration Requirements

**Critical Fields for MCP Configuration:**
- `servers`: Root object containing all MCP server configurations
- `name`: Human-readable name for the server (required by Trae)
- `type`: Must be "http" for HTTP-based MCP servers
- `url`: Complete endpoint URL including `/mcp/` path
- `headers`: Authentication and content-type headers

**Required Headers:**
- `imaging_api_key`: Your CAST Imaging API key for authentication

**Optional Headers (not required for basic setup):**
- `Content-Type`: "application/json"
- `Accept`: "application/json"

### Step 4: Common Issues and Solutions

#### Issue 1: "Missing mcpservers field"
- **Cause**: Using `mcpServers` instead of `servers`
- **Solution**: Use `servers` as the root configuration object

#### Issue 2: "Missing server fields"
- **Cause**: Missing required `name` field
- **Solution**: Add `name` field to each server configuration

#### Issue 3: "Incomplete configuration"
- **Cause**: Empty or malformed JSON file
- **Solution**: Ensure valid JSON structure with all required fields

#### Issue 4: Connection failures
- **Cause**: Incorrect URL or missing `/mcp/` endpoint
- **Solution**: Use complete URL: `http://localhost:8282/mcp/`

### Step 5: Verification Steps

1. **Check File Existence**:
   ```powershell
   Test-Path "C:\Users\[USERNAME]\AppData\Roaming\Trae\User\mcp.json"
   ```

2. **Verify File Content**:
   ```powershell
   Get-Content "C:\Users\[USERNAME]\AppData\Roaming\Trae\User\mcp.json"
   ```

3. **Check File Size** (should not be 0):
   ```powershell
   (Get-ItemProperty "C:\Users\[USERNAME]\AppData\Roaming\Trae\User\mcp.json").Length
   ```

### Step 6: Testing the Setup

1. **Restart Trae AI** after configuration changes
2. **Test MCP Connection** by trying to access CAST Imaging features
3. **Verify Available Applications**:
   - Use MCP tools to list applications
   - Confirm access to CAST Imaging data

## Available Applications (Example Output)

Once properly configured, you should be able to access applications like:
- Webgoat_v3
- HRMGMT_COB
- Microservice-SampleApp
- TicketMonster
- And others...

## Best Practices

1. **Security**: Keep API keys secure and never commit them to version control
2. **Backup**: Save working configurations before making changes
3. **Documentation**: Document any custom configurations for team members
4. **Testing**: Always test configuration changes in a development environment first

## Troubleshooting Checklist

- [ ] CAST Imaging server is running on localhost:8282
- [ ] MCP endpoint `/mcp/` is accessible
- [ ] API key is valid and properly formatted
- [ ] Global mcp.json uses `servers` (not `mcpServers`)
- [ ] Server configuration includes required `name` field
- [ ] URL includes complete path with `/mcp/`
- [ ] JSON syntax is valid (no trailing commas, proper quotes)
- [ ] File permissions allow Trae to read the configuration
- [ ] Trae AI has been restarted after configuration changes

## Additional Resources

- CAST Imaging Documentation
- Model Context Protocol Specification
- Trae AI MCP Integration Guide

---

*This guide was created based on the actual setup experience and troubleshooting steps performed during the CAST Imaging MCP server configuration process.*

# for more details on manual setings, lease refer to https://docs.trae.ai/ide/model-context-protocol