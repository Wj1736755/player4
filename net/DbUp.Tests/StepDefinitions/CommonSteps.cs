using System;
using System.IO;
using Reqnroll;
using Xunit;

namespace DbUp.Tests.StepDefinitions;

[Binding]
public sealed class CommonSteps
{
    private readonly ScenarioContext _scenarioContext;

    public CommonSteps(ScenarioContext scenarioContext)
    {
        _scenarioContext = scenarioContext;
    }

    [Then("the operation should be rejected")]
    public void ThenTheOperationShouldBeRejected()
    {
        if (!_scenarioContext.ContainsKey("Exception"))
        {
            Assert.Fail("Expected an exception to be thrown, but none was captured");
            return;
        }
        
        var capturedException = _scenarioContext.Get<Exception>("Exception");
        Assert.NotNull(capturedException);
    }

    [Then("the output location should be created automatically")]
    public void ThenTheOutputLocationShouldBeCreatedAutomatically()
    {
        string? directoryPath = null;
        
        if (_scenarioContext.ContainsKey("DifferencesScriptPath"))
        {
            var outputPath = _scenarioContext.Get<string>("DifferencesScriptPath");
            if (outputPath != null)
            {
                directoryPath = Path.GetDirectoryName(outputPath);
            }
        }
        
        if (directoryPath == null && _scenarioContext.ContainsKey("SnapshotOutputPath"))
        {
            var outputPath = _scenarioContext.Get<string>("SnapshotOutputPath");
            if (outputPath != null)
            {
                directoryPath = Path.GetDirectoryName(outputPath);
            }
        }
        
        if (string.IsNullOrEmpty(directoryPath))
        {
            Assert.Fail("Could not determine directory path from scenario context");
            return;
        }
        
        Assert.True(Directory.Exists(directoryPath), $"Directory should be created at: {directoryPath}");
    }
}

