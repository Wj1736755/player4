using System;
using System.IO;
using System.Text.RegularExpressions;
using Microsoft.SqlServer.Dac;
using RollbackGenerator.Exceptions;
using Stln.DbUp.Extensions;

namespace RollbackGenerator.DatabaseSchemasComparison
{
    public sealed class SnapshotComparator(ILogger logger)
    {
        private readonly ILogger _logger = logger ?? throw new ArgumentNullException(nameof(logger));

        public bool AreIdentical(string snapshotAPath, string snapshotBPath)
        {
            ValidateSnapshotPaths(snapshotAPath, snapshotBPath);

            var differencesScript = GenerateDifferencesScript(snapshotAPath, snapshotBPath);
            var hasDifferences = HasMeaningfulDifferences(differencesScript);

            return !hasDifferences;
        }

        public void GenerateDifferencesScript(
            string snapshotAPath,
            string snapshotBPath,
            string outputPath)
        {
            ValidateSnapshotPaths(snapshotAPath, snapshotBPath);
            ValidateOutputPath(outputPath);

            EnsureOutputDirectoryExists(outputPath);

            var differencesScript = GenerateDifferencesScript(snapshotAPath, snapshotBPath);

            File.WriteAllText(outputPath, differencesScript);

            var hasDifferences = HasMeaningfulDifferences(differencesScript);
            if (hasDifferences)
            {
                _logger.LogInfo($"Differences found between snapshots. Script saved to: {outputPath}");
            }
            else
            {
                _logger.LogInfo($"Snapshots are identical. Empty script saved to: {outputPath}");
            }
        }

        private static void ValidateSnapshotPaths(string snapshotAPath, string snapshotBPath)
        {
            if (string.IsNullOrWhiteSpace(snapshotAPath))
                throw new EmptySnapshotPathException("Snapshot A path cannot be null or empty", nameof(snapshotAPath));

            if (string.IsNullOrWhiteSpace(snapshotBPath))
                throw new EmptySnapshotPathException("Snapshot B path cannot be null or empty", nameof(snapshotBPath));

            if (!File.Exists(snapshotAPath))
                throw new SnapshotFileNotFoundException($"Snapshot A file not found: {snapshotAPath}", snapshotAPath);

            if (!File.Exists(snapshotBPath))
                throw new SnapshotFileNotFoundException($"Snapshot B file not found: {snapshotBPath}", snapshotBPath);
        }

        private static void ValidateOutputPath(string outputPath)
        {
            if (string.IsNullOrWhiteSpace(outputPath))
                throw new EmptyOutputPathException(nameof(outputPath));
        }

        private static void EnsureOutputDirectoryExists(string outputPath)
        {
            var directory = Path.GetDirectoryName(outputPath);
            if (!string.IsNullOrEmpty(directory) && !Directory.Exists(directory))
            {
                Directory.CreateDirectory(directory);
            }
        }

        private string GenerateDifferencesScript(string snapshotAPath, string snapshotBPath)
        {
            using var snapshotA = DacPackage.Load(snapshotAPath, DacSchemaModelStorageType.Memory, FileAccess.Read);
            using var snapshotB = DacPackage.Load(snapshotBPath, DacSchemaModelStorageType.Memory, FileAccess.Read);

            var tempScriptPath = Path.Combine(Path.GetTempPath(), $"Differences_{Guid.NewGuid():N}.sql");

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

                using (var scriptStream = new FileStream(tempScriptPath, FileMode.Create, FileAccess.Write))
                {
                    DacServices.GenerateDeployScript(
                        scriptStream,
                        snapshotB,
                        snapshotA,
                        "Differences",
                        deployOptions);

                    scriptStream.Flush();
                }

                var script = File.ReadAllText(tempScriptPath) ?? string.Empty;
                return NormalizeDifferencesScript(script);
            }
            catch (Exception ex)
            {
                _logger.LogError($"Failed to generate differences script: {ex.Message}", ex);
                throw new SnapshotComparisonException($"Failed to generate differences script: {ex.Message}", ex);
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

        private static bool HasMeaningfulDifferences(string script)
        {
            var normalized = NormalizeDifferencesScript(script);
            return !string.IsNullOrWhiteSpace(normalized);
        }

        private static string NormalizeDifferencesScript(string script)
        {
            if (string.IsNullOrWhiteSpace(script))
                return string.Empty;

            var lines = script.Split(new[] { '\r', '\n' }, StringSplitOptions.None);
            var outputLines = new System.Collections.Generic.List<string>();

            foreach (var line in lines)
            {
                var trimmed = line.Trim();

                if (string.IsNullOrWhiteSpace(trimmed))
                    continue;

                // Skip pure comments when normalizing – they are not treated as differences
                if (trimmed.StartsWith("--") || trimmed.StartsWith("/*"))
                    continue;

                // Treat only real DDL/DML as meaningful differences
                if (IsMeaningfulSqlLine(trimmed))
                {
                    outputLines.Add(line);
                }
            }

            return string.Join(Environment.NewLine, outputLines);
        }

        private static bool IsMeaningfulSqlLine(string trimmedLine)
        {
            // Very simple heuristic: look for common DDL/DML starters
            // to distinguish real schema changes from boilerplate SET/PRINT/etc.
            if (trimmedLine.Length == 0)
                return false;

            static bool StartsWithKeyword(string line, string keyword) =>
                line.StartsWith(keyword, StringComparison.OrdinalIgnoreCase);

            return
                StartsWithKeyword(trimmedLine, "CREATE ") ||
                StartsWithKeyword(trimmedLine, "ALTER ") ||
                StartsWithKeyword(trimmedLine, "DROP ") ||
                StartsWithKeyword(trimmedLine, "INSERT ") ||
                StartsWithKeyword(trimmedLine, "UPDATE ") ||
                StartsWithKeyword(trimmedLine, "DELETE ") ||
                StartsWithKeyword(trimmedLine, "MERGE ") ||
                StartsWithKeyword(trimmedLine, "EXEC ") ||
                StartsWithKeyword(trimmedLine, "EXECUTE ");
        }
    }
}

