using System;
using System.Collections.Generic;
using System.Data;
using System.Data.Common;
using DbUp.Builder;
using DbUp.SqlServer;
using Stln.DbUp.Extensions;

namespace DbUp
{
    public static class DbUpConfiguration
{
    public static UpgradeEngineBuilder WithCustomTrackingTable(
        this UpgradeEngineBuilder upgradeEngineBuilder,
        string connectionString,
        ILogger logger,
        string schema,
        string tableName)
    {
        Func<IDbCommand> dbCommandFactory = () =>
        {
            var connection = new Microsoft.Data.SqlClient.SqlConnection(connectionString);
            connection.Open();
            return connection.CreateCommand();
        };
        
        var journal = new CustomJournal(
            dbCommandFactory,
            schema,
            tableName,
            logger);

        return upgradeEngineBuilder.JournalTo(journal);
    }

    public static UpgradeEngineBuilder DeployChangesTo(
        string connectionString,
        ILogger logger,
        string schema,
        string tableName)
    {
        return DeployChanges.To
            .SqlDatabase(connectionString)
            .WithCustomTrackingTable(connectionString, logger, schema, tableName);
    }
    }
}

