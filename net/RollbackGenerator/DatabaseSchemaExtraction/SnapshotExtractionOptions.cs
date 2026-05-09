using System;
using System.Collections.Generic;

namespace RollbackGenerator.DatabaseSchemaExtraction
{
    public static class SnapshotExtractionOptions
    {
        public static readonly ISet<string> IncludedObjectTypes = new HashSet<string>(StringComparer.OrdinalIgnoreCase)
        {
            "Schema",
            "Table",
            "PrimaryKeyConstraint",
            "ForeignKeyConstraint",
            "UniqueConstraint",
            "CheckConstraint",
            "DefaultConstraint",
            "Index",
            "Statistics",
            "FullTextIndex",
            "Trigger",
            "Procedure",
            "ScalarFunction",
            "TableValuedFunction",
            "SqlInlineTableValuedFunction",
            "SqlTableValuedFunction",
            "View",
            "UserDefinedType",
            "UserDefinedTableType",
            "XmlSchemaCollection",
            "Synonym",
            "Sequence",
            "Rule",
            "Default"
        };
        
        public static readonly ISet<string> SecurityObjectTypes = new HashSet<string>(StringComparer.OrdinalIgnoreCase)
        {
            "User",
            "Login",
            "Role",
            "RoleMembership",
            "Permission",
            "DatabaseRole",
            "ApplicationRole",
            "SqlUser",
            "WindowsUser",
            "Certificate",
            "AsymmetricKey",
            "SymmetricKey",
            "MasterKey",
            "DatabaseEncryptionKey",
            "Audit",
            "AuditSpecification",
            "ServerAuditSpecification",
            "ServerRole",
            "ServerRoleMembership",
            "SchemaPermission",
            "ObjectPermission"
        };
        
        public static readonly ISet<string> ServerLevelObjectTypes = new HashSet<string>(StringComparer.OrdinalIgnoreCase)
        {
            "ServerRole",
            "ServerRoleMembership",
            "ServerAudit",
            "ServerAuditSpecification",
            "Login"
        };
        
        public static class Filtering
        {
            public static readonly ISet<string> ExcludeSchemas = new HashSet<string>(StringComparer.OrdinalIgnoreCase)
            {
                "sys",
                "INFORMATION_SCHEMA"
            };
        }
    }
}

