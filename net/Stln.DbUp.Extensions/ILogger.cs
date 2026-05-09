using System;
using DbUp.Engine.Output;

namespace Stln.DbUp.Extensions
{
    /// <summary>
    /// Extension methods and helpers to work with DbUp's <see cref="IUpgradeLog"/>.
    /// </summary>
    public static class LoggerExtensions
    {
        public static void LogInfo(this IUpgradeLog upgradeLog, string message)
        {
            upgradeLog.LogInformation(message);
        }

        public static void LogWarning(this IUpgradeLog upgradeLog, string message)
        {
            upgradeLog.LogWarning(message);
        }

        public static void LogWarning(this IUpgradeLog upgradeLog, string message, Exception exception)
        {
            upgradeLog.LogWarning(message);
            upgradeLog.LogWarning($"Exception: {exception}");
        }

        public static void LogError(this IUpgradeLog upgradeLog, string message, Exception exception)
        {
            upgradeLog.LogError(message);
            upgradeLog.LogError($"Exception: {exception}");
        }

        /// <summary>
        /// Adapts any <see cref="IUpgradeLog"/> to <see cref="ILogger"/>.
        /// </summary>
        public static ILogger AsStlnLogger(this IUpgradeLog upgradeLog)
        {
            if (upgradeLog == null) throw new ArgumentNullException(nameof(upgradeLog));
            return upgradeLog as ILogger ?? new UpgradeLogAdapter(upgradeLog);
        }
    }

    /// <summary>
    /// ILogger is an alias for DbUp's <see cref="IUpgradeLog"/>.
    /// </summary>
    public interface ILogger : IUpgradeLog
    {
    }

    /// <summary>
    /// Simple adapter that forwards calls to an underlying <see cref="IUpgradeLog"/>.
    /// </summary>
    internal sealed class UpgradeLogAdapter : ILogger
    {
        private readonly IUpgradeLog _inner;

        public UpgradeLogAdapter(IUpgradeLog inner)
        {
            _inner = inner ?? throw new ArgumentNullException(nameof(inner));
        }

        public void LogTrace(string format, params object[] args) => _inner.LogTrace(format, args);

        public void LogDebug(string format, params object[] args) => _inner.LogDebug(format, args);

        public void LogInformation(string format, params object[] args) => _inner.LogInformation(format, args);

        public void LogWarning(string format, params object[] args) => _inner.LogWarning(format, args);

        public void LogError(string format, params object[] args) => _inner.LogError(format, args);

        public void LogError(Exception exception, string format, params object[] args) =>
            _inner.LogError(exception, format, args);
    }
}

