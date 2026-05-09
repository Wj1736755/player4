using System;
using System.IO;
using Microsoft.SqlServer.Dac;
using Stln.DbUp.Extensions;
using DbUp.Exceptions;

namespace DbUp.RollbackGeneration
{
    public sealed class DacFxRollbackGenerator
{
    private readonly ILogger _logger;
    
    public DacFxRollbackGenerator(ILogger logger)
    {
        _logger = logger ?? throw new ArgumentNullException(nameof(logger));
    }
    
    public string GenerateRollbackScript(
        DacPackage sourceModel,
        DacPackage targetModel,
        string connectionString)
    {
        var tempScriptPath = Path.Combine(Path.GetTempPath(), $"RollbackScript_{Guid.NewGuid():N}.sql");
        
        try
        {
            var deployOptions = new DacDeployOptions
            {
                IgnorePermissions = true,
                IgnoreRoleMembership = true,
                IgnoreLoginSids = true,
                IgnorePartitionSchemes = true,
                IgnoreObjectPlacementOnPartitionScheme = true
            };
            
            using var deployScriptStream = new FileStream(tempScriptPath, FileMode.Create, FileAccess.Write);
            
            DacServices.GenerateDeployScript(
                deployScriptStream,
                sourceModel,
                targetModel,
                "temp",
                deployOptions);
            
            deployScriptStream.Flush();
            
            var rollbackScript = File.ReadAllText(tempScriptPath);
            
            return rollbackScript ?? string.Empty;
        }
        catch (Exception ex)
        {
            throw new RollbackGenerationException($"Failed to generate rollback script: {ex.Message}", ex);
        }
        finally
        {
            if (File.Exists(tempScriptPath))
            {
                try 
                { 
                    File.Delete(tempScriptPath); 
                } 
                catch (Exception ex)
                {
                    _logger.LogWarning($"Failed to delete temporary script file", ex);
                }
            }
        }
    }
    }
}

