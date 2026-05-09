using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Reflection;
using System.Text;
using DbUp;
using DbUp.Engine;
using DbUp.Engine.Transactions;
using Xunit;

namespace DbUp.Tests
{
    public class RollbackAwareEmbeddedScriptsProviderTests
{
    private Assembly CreateTestAssembly()
    {
        return Assembly.GetExecutingAssembly();
    }

    [Fact]
    public void GetScripts_ExcludesRollbackScriptsFromExecution()
    {
        var assembly = CreateTestAssembly();
        var provider = new RollbackAwareEmbeddedScriptsProvider(assembly, s => !s.EndsWith(".rollback.sql", StringComparison.OrdinalIgnoreCase));
        
        IConnectionManager? connectionManager = null;
        var scripts = provider.GetScripts(connectionManager!).ToList();
        
        Assert.All(scripts, script => Assert.False(script.Name.EndsWith(".rollback.sql", StringComparison.OrdinalIgnoreCase)));
    }

    [Fact]
    public void GetScripts_ReturnsSqlScriptWithRollbackInstances()
    {
        var assembly = CreateTestAssembly();
        var provider = new RollbackAwareEmbeddedScriptsProvider(assembly, s => !s.EndsWith(".rollback.sql", StringComparison.OrdinalIgnoreCase));
        
        IConnectionManager? connectionManager = null;
        var scripts = provider.GetScripts(connectionManager!).ToList();
        
        Assert.All(scripts, script => Assert.IsType<SqlScriptWithRollback>(script));
    }
    }
}

