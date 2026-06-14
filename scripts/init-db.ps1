param(
    [string]$ContainerName = "bank_opengauss",
    [string]$DbUser = "bank_admin",
    [string]$DbPassword = "Gauss@123",
    [string]$DbName = "bank_system"
)

$ErrorActionPreference = "Stop"

function Invoke-GsqlFile {
    param(
        [string]$FilePath,
        [string]$RemoteName
    )

    if (-not (Test-Path $FilePath)) {
        throw "SQL file not found: $FilePath"
    }

    $remotePath = "/tmp/$RemoteName"
    docker cp $FilePath "${ContainerName}:$remotePath"
    docker exec `
        -e LD_LIBRARY_PATH=/usr/local/opengauss/lib:/scws/lib `
        $ContainerName `
        /usr/local/opengauss/bin/gsql `
        -U $DbUser `
        -W $DbPassword `
        -d $DbName `
        -v ON_ERROR_STOP=1 `
        -f $remotePath
}

Invoke-GsqlFile "src/main/resources/schema.sql" "schema.sql"
Invoke-GsqlFile "src/main/resources/data.sql" "data.sql"

Write-Host "openGauss schema and seed data initialized successfully."
