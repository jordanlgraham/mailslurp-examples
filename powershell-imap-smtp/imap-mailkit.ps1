# Now load MailKit
. .\_helpers.ps1
$scriptPath = Split-Path -Path $script:MyInvocation.MyCommand.Definition -Parent

LoadMailKit($scriptPath)

try
{
    $imapClient = New-Object MailKit.Net.Imap.ImapClient
    Write-Host "MailKit is loaded properly. ImapClient instance created successfully."
}
catch
{
    Write-Error "Failed to create ImapClient instance. MailKit may not be loaded properly: $_"
}

$apiKey = $env:API_KEY

# Create a new SMTP inbox and parse JSON response
$response = Invoke-RestMethod -Method Post -Uri "https://api.mailslurp.com/inboxes?inboxType=SMTP_INBOX" -Headers @{ "x-api-key" = $apiKey }
$inboxId = $response.id
$emailAddress = $response.emailAddress

Write-Host "Sending email from $inboxId to $emailAddress"
Invoke-RestMethod -Method Post -Uri "https://api.mailslurp.com/sendEmailQuery?inboxId=$inboxId&to=$emailAddress&subject=test" -Headers @{ "x-api-key" = $apiKey }
Write-Host "Waiting for email in inbox $inboxId"
Invoke-RestMethod -Method Get -Uri "https://api.mailslurp.com/waitForLatestEmail?inboxId=$inboxId" -Headers @{ "x-api-key" = $apiKey }

# Fetch environment variables for inbox and account access
Invoke-RestMethod -Method Get -Uri "https://api.mailslurp.com/inboxes/imap-smtp-access/env?inboxId=$inboxId" -Headers @{ "x-api-key" = $apiKey } -OutFile ".env.inbox"
Invoke-RestMethod -Method Get -Uri "https://api.mailslurp.com/inboxes/imap-smtp-access/env" -Headers @{ "x-api-key" = $apiKey } -OutFile ".env.account"

# Load environment variables from downloaded files
Load-EnvironmentVariables -filePath ".\.env.inbox"

Write-Host "Connect insecure"
#<gen>mailkit_connect_insecure
$client = New-Object MailKit.Net.Imap.ImapClient
$client.ServerCertificateValidationCallback = { $true }
$client.Connect($env:IMAP_SERVER_HOST, [int32]$env:IMAP_SERVER_PORT, [MailKit.Security.SecureSocketOptions]::None)
$client.Authenticate($env:IMAP_USERNAME, $env:IMAP_PASSWORD)
#</gen>
#Write-Host "Connect secure"
##<gen>mailkit_connect_secure
#$clientS = New-Object MailKit.Net.Imap.ImapClient
#$clientS.ServerCertificateValidationCallback = { $true }
#[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls11
#$clientS.Connect($env:SECURE_IMAP_SERVER_HOST, [int32]$env:SECURE_IMAP_SERVER_PORT,  [MailKit.Security.SecureSocketOptions]::Auto)
#$clientS.Authenticate($env:SECURE_IMAP_USERNAME, $env:SECURE_IMAP_PASSWORD)
##</gen>

# Fetch and list all messages' headers
#<gen>mailkit_select
$inbox = $client.Inbox.Open([MailKit.FolderAccess]::ReadOnly)
Write-Host "Total messages: $( $client.Inbox.Count )"
#</gen>
#<gen>mailkit_fetch_messages
$fetchRequest = New-Object MailKit.FetchRequest
$messages = $client.Inbox.Fetch(0, -1, $fetchRequest)
foreach ($message in $messages)
{
    $from = $message.Envelope.From.ToString()
    $subject = $message.Envelope.Subject
    Write-Host "From: $from Subject: $subject"
}
#</gen>

# Search for unseen messages
function Search-Unseen($client)
{
    $client.Inbox.Open([MailKit.FolderAccess]::ReadOnly)
    $unseenIds = $client.Inbox.Search([MailKit.Search.SearchQuery]::NotSeen)
    return $unseenIds
}

# Select INBOX and list unseen messages
#<gen>mailkit_select_and_list_unseen($client) {
$client.Inbox.Open([MailKit.FolderAccess]::ReadOnly)
$unseenMessages = Search-Unseen $client
Write-Host "Unseen Messages: $unseenMessages.Count"
#</gen>

# Modify message flags
$client.Inbox.Open([MailKit.FolderAccess]::ReadWrite)
$client.Inbox.AddFlags(1, $client.Inbox.Count, $flag, "")


$client.Disconnect($true)
$clientS.Disconnect($true)
