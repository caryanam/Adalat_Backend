$baseUrl = "http://localhost:8082/api/customer/legal-assistance/sessions"
$customerId = 100

function Start-Session {
    $response = Invoke-RestMethod -Uri "$baseUrl/init?customerId=$customerId" -Method Post -ContentType "application/json"
    return $response.sessionId
}

function Send-Message {
    param (
        [int]$sessionId,
        [string]$message
    )
    
    $body = @{
        message = $message
    } | ConvertTo-Json

    $response = Invoke-RestMethod -Uri "$baseUrl/$sessionId/messages?customerId=$customerId" -Method Post -Body $body -ContentType "application/json"
    
    Write-Host "---------------------------------------------------"
    Write-Host "USER: $message"
    Write-Host "AI: $($response.assistantMessage)"
    Write-Host "STATUS: $($response.status) | CATEGORY: $($response.primaryCategory) | QUESTIONS: $($response.questionCount)"
    if ($response.customerSummary) {
        Write-Host "SUMMARY: $($response.customerSummary)"
    }
    Write-Host "---------------------------------------------------"
}

Write-Host "=== TEST A: SALARY ==="
$sessionIdA = Start-Session
Send-Message -sessionId $sessionIdA -message "I have not been paid my salary for the last 3 months."
Send-Message -sessionId $sessionIdA -message "I work full time, the pending amount is around 90,000 rupees."

Write-Host "=== TEST B: TENANCY ==="
$sessionIdB = Start-Session
Send-Message -sessionId $sessionIdB -message "My landlord is forcing me to leave the house in 3 days without notice."
Send-Message -sessionId $sessionIdB -message "I have a registered 11 month agreement."

Write-Host "=== TEST C: CRIMINAL ==="
$sessionIdC = Start-Session
Send-Message -sessionId $sessionIdC -message "My neighbor hit me during an argument and I have a head injury."
Send-Message -sessionId $sessionIdC -message "I haven't gone to the police yet."

Write-Host "=== TEST D: OUT OF SCOPE ==="
$sessionIdD = Start-Session
Send-Message -sessionId $sessionIdD -message "Can you write a poem about the monsoon season in Mumbai?"

