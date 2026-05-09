-- Migration Script 001: Create initial tables with relationships
CREATE TABLE [dbo].[Users] (
    [Id] INT IDENTITY(1,1) PRIMARY KEY,
    [Name] NVARCHAR(100) NOT NULL,
    [Email] NVARCHAR(255) NOT NULL,
    [CreatedAt] DATETIME2 NOT NULL DEFAULT GETUTCDATE()
);

CREATE TABLE [dbo].[Products] (
    [Id] INT IDENTITY(1,1) PRIMARY KEY,
    [SKU] NVARCHAR(50) NOT NULL,
    [Name] NVARCHAR(200) NOT NULL,
    [Price] DECIMAL(18,2) NOT NULL
);

-- Migration Script 002: Add columns with different types and create indexes
ALTER TABLE [dbo].[Users]
ADD [PhoneNumber] NVARCHAR(20) NULL,
    [DateOfBirth] DATE NULL,
    [Salary] DECIMAL(18,2) NULL,
    [LastLogin] DATETIME2 NULL,
    [IsActive] BIT NOT NULL DEFAULT 1;

CREATE INDEX [IX_Users_Email] ON [dbo].[Users]([Email]);
CREATE INDEX [IX_Users_IsActive] ON [dbo].[Users]([IsActive]) WHERE [IsActive] = 1;

-- Migration Script 003: Create table with foreign key and modify existing table
CREATE TABLE [dbo].[Orders] (
    [Id] INT IDENTITY(1,1) PRIMARY KEY,
    [UserId] INT NOT NULL,
    [OrderDate] DATETIME2 NOT NULL,
    [TotalAmount] DECIMAL(18,2) NOT NULL,
    CONSTRAINT [FK_Orders_Users] FOREIGN KEY ([UserId]) REFERENCES [dbo].[Users]([Id])
);

ALTER TABLE [dbo].[Products]
ADD [CategoryId] INT NULL,
    [Description] NVARCHAR(MAX) NULL,
    [Weight] DECIMAL(10,3) NULL;

-- Migration Script 004: Change column types and add constraints
ALTER TABLE [dbo].[Users]
ALTER COLUMN [Email] NVARCHAR(500) NOT NULL;

ALTER TABLE [dbo].[Users]
ALTER COLUMN [Salary] DECIMAL(19,4) NULL;

ALTER TABLE [dbo].[Products]
ALTER COLUMN [Price] DECIMAL(20,4) NOT NULL;

ALTER TABLE [dbo].[Users]
ADD CONSTRAINT [UQ_Users_Email] UNIQUE ([Email]);

ALTER TABLE [dbo].[Products]
ADD CONSTRAINT [CK_Products_Price] CHECK ([Price] >= 0);

-- Migration Script 005: Add computed columns and modify indexes
ALTER TABLE [dbo].[Orders]
ADD [Quantity] INT NOT NULL DEFAULT 1,
    [UnitPrice] DECIMAL(18,2) NOT NULL,
    [TotalPrice] AS ([Quantity] * [UnitPrice]) PERSISTED;

DROP INDEX [IX_Users_Email] ON [dbo].[Users];
CREATE UNIQUE INDEX [UQ_Users_Email_Unique] ON [dbo].[Users]([Email]) WHERE [IsActive] = 1;

-- Migration Script 006: Create additional tables and modify existing relationships
CREATE TABLE [dbo].[Categories] (
    [Id] INT IDENTITY(1,1) PRIMARY KEY,
    [Name] NVARCHAR(100) NOT NULL,
    [Description] NVARCHAR(500) NULL
);

ALTER TABLE [dbo].[Products]
ADD CONSTRAINT [FK_Products_Categories] FOREIGN KEY ([CategoryId]) REFERENCES [dbo].[Categories]([Id]);

CREATE TABLE [dbo].[OrderItems] (
    [Id] INT IDENTITY(1,1) PRIMARY KEY,
    [OrderId] INT NOT NULL,
    [ProductId] INT NOT NULL,
    [Quantity] INT NOT NULL,
    [UnitPrice] DECIMAL(18,2) NOT NULL,
    CONSTRAINT [FK_OrderItems_Orders] FOREIGN KEY ([OrderId]) REFERENCES [dbo].[Orders]([Id]),
    CONSTRAINT [FK_OrderItems_Products] FOREIGN KEY ([ProductId]) REFERENCES [dbo].[Products]([Id])
);

-- Migration Script 007: Change primary key and modify column types
ALTER TABLE [dbo].[Users]
ADD [UserId] UNIQUEIDENTIFIER NOT NULL DEFAULT NEWID();

ALTER TABLE [dbo].[Users]
DROP CONSTRAINT [PK__Users__3214EC07];

ALTER TABLE [dbo].[Users]
ADD CONSTRAINT [PK_Users_UserId] PRIMARY KEY ([UserId]);

CREATE INDEX [IX_Users_Id] ON [dbo].[Users]([Id]);

-- Migration Script 008: Add multiple columns with different datetime types and modify existing
ALTER TABLE [dbo].[Orders]
ADD [CreatedAt] DATETIME2 NOT NULL DEFAULT GETUTCDATE(),
    [UpdatedAt] DATETIME2 NULL,
    [ShippedDate] DATE NULL,
    [DeliveredDate] DATETIME NULL,
    [ProcessingTime] TIME NULL;

ALTER TABLE [dbo].[Products]
ALTER COLUMN [Name] NVARCHAR(500) NOT NULL;

ALTER TABLE [dbo].[Products]
ADD [CreatedAt] DATETIME2 NOT NULL DEFAULT GETUTCDATE(),
    [ModifiedAt] DATETIME2 NULL;

-- Migration Script 009: Change decimal precision and add filtered indexes
ALTER TABLE [dbo].[Orders]
ALTER COLUMN [TotalAmount] DECIMAL(22,6) NOT NULL;

ALTER TABLE [dbo].[Products]
ALTER COLUMN [Price] DECIMAL(25,6) NOT NULL;

DROP INDEX [IX_Users_IsActive] ON [dbo].[Users];
CREATE INDEX [IX_Users_Active_Email] ON [dbo].[Users]([Email], [IsActive]) 
INCLUDE ([Name], [PhoneNumber]) 
WHERE [IsActive] = 1;

CREATE INDEX [IX_Orders_ActiveOrders] ON [dbo].[Orders]([OrderDate], [TotalAmount])
WHERE [TotalAmount] > 100;

-- Migration Script 010: Drop columns and constraints, add new ones
ALTER TABLE [dbo].[Users]
DROP CONSTRAINT [UQ_Users_Email];

ALTER TABLE [dbo].[Users]
DROP COLUMN [PhoneNumber];

ALTER TABLE [dbo].[Users]
ADD [MobilePhone] NVARCHAR(20) NULL,
    [WorkPhone] NVARCHAR(20) NULL,
    [HomePhone] NVARCHAR(20) NULL;

ALTER TABLE [dbo].[Users]
ADD CONSTRAINT [UQ_Users_Email_New] UNIQUE ([Email], [IsActive]);

-- Migration Script 011: Modify foreign keys and add cascade options
ALTER TABLE [dbo].[Orders]
DROP CONSTRAINT [FK_Orders_Users];

ALTER TABLE [dbo].[Orders]
ADD CONSTRAINT [FK_Orders_Users_Cascade] FOREIGN KEY ([UserId]) 
REFERENCES [dbo].[Users]([Id]) ON DELETE CASCADE ON UPDATE CASCADE;

ALTER TABLE [dbo].[OrderItems]
DROP CONSTRAINT [FK_OrderItems_Orders];

ALTER TABLE [dbo].[OrderItems]
ADD CONSTRAINT [FK_OrderItems_Orders_Cascade] FOREIGN KEY ([OrderId]) 
REFERENCES [dbo].[Orders]([Id]) ON DELETE CASCADE;

-- Migration Script 012: Change nvarchar sizes and add check constraints
ALTER TABLE [dbo].[Users]
ALTER COLUMN [Name] NVARCHAR(200) NOT NULL;

ALTER TABLE [dbo].[Products]
ALTER COLUMN [SKU] NVARCHAR(100) NOT NULL;

ALTER TABLE [dbo].[Categories]
ALTER COLUMN [Name] NVARCHAR(200) NOT NULL,
    [Description] NVARCHAR(1000) NULL;

ALTER TABLE [dbo].[Orders]
ADD CONSTRAINT [CK_Orders_TotalAmount_Positive] CHECK ([TotalAmount] > 0);

ALTER TABLE [dbo].[OrderItems]
ADD CONSTRAINT [CK_OrderItems_Quantity] CHECK ([Quantity] > 0);

-- Migration Script 013: Add indexes with includes and modify existing
CREATE INDEX [IX_Products_Category_SKU] ON [dbo].[Products]([CategoryId], [SKU])
INCLUDE ([Name], [Price]);

DROP INDEX [IX_Users_Active_Email] ON [dbo].[Users];
CREATE INDEX [IX_Users_Email_Active] ON [dbo].[Users]([Email])
INCLUDE ([Name], [DateOfBirth], [IsActive])
WHERE [IsActive] = 1;

CREATE INDEX [IX_Orders_User_Date] ON [dbo].[Orders]([UserId], [OrderDate])
INCLUDE ([TotalAmount], [Quantity]);

-- Migration Script 014: Create stored procedures and modify tables
CREATE PROCEDURE [dbo].[GetUserOrders]
    @UserId INT
AS
BEGIN
    SELECT o.*, u.[Name] AS UserName
    FROM [dbo].[Orders] o
    INNER JOIN [dbo].[Users] u ON o.[UserId] = u.[Id]
    WHERE o.[UserId] = @UserId;
END;

ALTER TABLE [dbo].[Users]
ADD [PreferredLanguage] NVARCHAR(10) NULL DEFAULT 'en',
    [Timezone] NVARCHAR(50) NULL;

ALTER TABLE [dbo].[Orders]
ADD [Currency] NVARCHAR(3) NOT NULL DEFAULT 'USD',
    [Status] NVARCHAR(50) NOT NULL DEFAULT 'Pending';

-- Migration Script 015: Change datetime to date and add computed columns
ALTER TABLE [dbo].[Users]
ADD [RegistrationDate] DATE NULL,
    [LastActivity] DATETIME2 NULL;

ALTER TABLE [dbo].[Orders]
ALTER COLUMN [OrderDate] DATE NOT NULL;

ALTER TABLE [dbo].[Orders]
ADD [DaysSinceOrder] AS (DATEDIFF(DAY, [OrderDate], GETDATE())) PERSISTED,
    [IsRecentOrder] AS (CASE WHEN [OrderDate] >= DATEADD(DAY, -30, GETDATE()) THEN 1 ELSE 0 END) PERSISTED;

-- Migration Script 016: Drop and recreate indexes with different options
DROP INDEX [IX_Products_Category_SKU] ON [dbo].[Products];
CREATE CLUSTERED INDEX [CIX_Products_Category] ON [dbo].[Products]([CategoryId], [Id]);

CREATE NONCLUSTERED INDEX [IX_Products_SKU_Name] ON [dbo].[Products]([SKU], [Name])
INCLUDE ([Price], [Description]);

ALTER TABLE [dbo].[Categories]
ADD [ParentCategoryId] INT NULL,
    [SortOrder] INT NOT NULL DEFAULT 0;

ALTER TABLE [dbo].[Categories]
ADD CONSTRAINT [FK_Categories_Parent] FOREIGN KEY ([ParentCategoryId]) REFERENCES [dbo].[Categories]([Id]);

-- Migration Script 017: Modify multiple columns and add constraints
ALTER TABLE [dbo].[Users]
ALTER COLUMN [DateOfBirth] DATETIME2 NULL;

ALTER TABLE [dbo].[Users]
ALTER COLUMN [LastLogin] DATETIME2 NULL;

ALTER TABLE [dbo].[Products]
ADD [StockQuantity] INT NOT NULL DEFAULT 0,
    [ReorderLevel] INT NOT NULL DEFAULT 10,
    [IsDiscontinued] BIT NOT NULL DEFAULT 0;

ALTER TABLE [dbo].[Products]
ADD CONSTRAINT [CK_Products_Stock] CHECK ([StockQuantity] >= 0),
    CONSTRAINT [CK_Products_ReorderLevel] CHECK ([ReorderLevel] >= 0);

-- Migration Script 018: Change decimal precision multiple times and add indexes
ALTER TABLE [dbo].[Orders]
ALTER COLUMN [UnitPrice] DECIMAL(22,6) NOT NULL;

ALTER TABLE [dbo].[OrderItems]
ALTER COLUMN [UnitPrice] DECIMAL(22,6) NOT NULL;

ALTER TABLE [dbo].[OrderItems]
ALTER COLUMN [Quantity] DECIMAL(10,2) NOT NULL;

CREATE INDEX [IX_OrderItems_Order_Product] ON [dbo].[OrderItems]([OrderId], [ProductId])
INCLUDE ([Quantity], [UnitPrice]);

CREATE INDEX [IX_Products_Discontinued] ON [dbo].[Products]([IsDiscontinued], [StockQuantity])
WHERE [IsDiscontinued] = 0;

-- Migration Script 019: Drop constraints and recreate with different options
ALTER TABLE [dbo].[Orders]
DROP CONSTRAINT [CK_Orders_TotalAmount_Positive];

ALTER TABLE [dbo].[Orders]
ADD CONSTRAINT [CK_Orders_TotalAmount_Range] CHECK ([TotalAmount] >= 0 AND [TotalAmount] <= 1000000);

ALTER TABLE [dbo].[OrderItems]
DROP CONSTRAINT [CK_OrderItems_Quantity];

ALTER TABLE [dbo].[OrderItems]
ADD CONSTRAINT [CK_OrderItems_Quantity_Range] CHECK ([Quantity] > 0 AND [Quantity] <= 10000);

ALTER TABLE [dbo].[Users]
ADD [Age] AS (DATEDIFF(YEAR, [DateOfBirth], GETDATE())) PERSISTED;

-- Migration Script 020: Create views and modify underlying tables
CREATE VIEW [dbo].[OrderSummary]
AS
SELECT 
    o.[Id],
    u.[Name] AS CustomerName,
    o.[OrderDate],
    o.[TotalAmount],
    o.[Status]
FROM [dbo].[Orders] o
INNER JOIN [dbo].[Users] u ON o.[UserId] = u.[Id];

ALTER TABLE [dbo].[Orders]
ADD [ShippingAddress] NVARCHAR(500) NULL,
    [BillingAddress] NVARCHAR(500) NULL,
    [Notes] NVARCHAR(MAX) NULL;

-- Migration Script 021: Change primary key to composite and modify relationships
ALTER TABLE [dbo].[OrderItems]
DROP CONSTRAINT [PK__OrderIte__3214EC07];

ALTER TABLE [dbo].[OrderItems]
ADD CONSTRAINT [PK_OrderItems_Composite] PRIMARY KEY ([OrderId], [ProductId]);

DROP INDEX [IX_OrderItems_Order_Product] ON [dbo].[OrderItems];

CREATE INDEX [IX_OrderItems_Product] ON [dbo].[OrderItems]([ProductId])
INCLUDE ([OrderId], [Quantity], [UnitPrice]);

-- Migration Script 022: Add triggers and modify table structure
CREATE TRIGGER [dbo].[TR_Orders_UpdateTimestamp]
ON [dbo].[Orders]
AFTER UPDATE
AS
BEGIN
    UPDATE [dbo].[Orders]
    SET [UpdatedAt] = GETUTCDATE()
    WHERE [Id] IN (SELECT [Id] FROM inserted);
END;

ALTER TABLE [dbo].[Users]
ADD [AccountBalance] DECIMAL(18,2) NOT NULL DEFAULT 0,
    [CreditLimit] DECIMAL(18,2) NULL,
    [PaymentMethod] NVARCHAR(50) NULL;

ALTER TABLE [dbo].[Products]
ADD [SupplierId] INT NULL,
    [Manufacturer] NVARCHAR(200) NULL;

-- Migration Script 023: Create schema and move objects
CREATE SCHEMA [Archive];

ALTER SCHEMA [Archive] TRANSFER [dbo].[OrderItems];

CREATE TABLE [dbo].[OrderItems] (
    [OrderId] INT NOT NULL,
    [ProductId] INT NOT NULL,
    [Quantity] DECIMAL(10,2) NOT NULL,
    [UnitPrice] DECIMAL(22,6) NOT NULL,
    [Discount] DECIMAL(5,2) NOT NULL DEFAULT 0,
    CONSTRAINT [PK_OrderItems_New] PRIMARY KEY ([OrderId], [ProductId]),
    CONSTRAINT [FK_OrderItems_Orders_New] FOREIGN KEY ([OrderId]) REFERENCES [dbo].[Orders]([Id]) ON DELETE CASCADE,
    CONSTRAINT [FK_OrderItems_Products_New] FOREIGN KEY ([ProductId]) REFERENCES [dbo].[Products]([Id])
);

-- Migration Script 024: Modify indexes with filters and includes
DROP INDEX [IX_Products_Discontinued] ON [dbo].[Products];
CREATE INDEX [IX_Products_Active_Stock] ON [dbo].[Products]([IsDiscontinued], [StockQuantity], [CategoryId])
INCLUDE ([Name], [Price], [SKU])
WHERE [IsDiscontinued] = 0 AND [StockQuantity] > 0;

DROP INDEX [IX_Orders_ActiveOrders] ON [dbo].[Orders];
CREATE INDEX [IX_Orders_Status_Date] ON [dbo].[Orders]([Status], [OrderDate])
INCLUDE ([TotalAmount], [UserId])
WHERE [Status] IN ('Pending', 'Processing', 'Shipped');

-- Migration Script 025: Change column types from nvarchar to different sizes
ALTER TABLE [dbo].[Users]
ALTER COLUMN [PreferredLanguage] NVARCHAR(20) NULL;

ALTER TABLE [dbo].[Users]
ALTER COLUMN [Timezone] NVARCHAR(100) NULL;

ALTER TABLE [dbo].[Orders]
ALTER COLUMN [Currency] NVARCHAR(10) NOT NULL;

ALTER TABLE [dbo].[Orders]
ALTER COLUMN [Status] NVARCHAR(100) NOT NULL;

ALTER TABLE [dbo].[Products]
ALTER COLUMN [Manufacturer] NVARCHAR(500) NULL;

-- Migration Script 026: Add unique constraints and modify indexes
ALTER TABLE [dbo].[Products]
ADD CONSTRAINT [UQ_Products_SKU] UNIQUE ([SKU]);

DROP INDEX [IX_Products_SKU_Name] ON [dbo].[Products];
CREATE UNIQUE INDEX [UQ_Products_SKU_Unique] ON [dbo].[Products]([SKU])
WHERE [IsDiscontinued] = 0;

ALTER TABLE [dbo].[Categories]
ADD CONSTRAINT [UQ_Categories_Name] UNIQUE ([Name]);

-- Migration Script 027: Modify datetime columns and add computed columns
ALTER TABLE [dbo].[Users]
ADD [EmailVerifiedAt] DATETIME2 NULL,
    [PhoneVerifiedAt] DATETIME2 NULL,
    [LastPasswordChange] DATETIME2 NULL;

ALTER TABLE [dbo].[Orders]
ALTER COLUMN [CreatedAt] DATETIME2(7) NOT NULL;

ALTER TABLE [dbo].[Orders]
ALTER COLUMN [UpdatedAt] DATETIME2(7) NULL;

ALTER TABLE [dbo].[Users]
ADD [DaysSinceLastLogin] AS (CASE WHEN [LastLogin] IS NULL THEN NULL ELSE DATEDIFF(DAY, [LastLogin], GETDATE()) END) PERSISTED;

-- Migration Script 028: Drop and recreate foreign keys with different options
ALTER TABLE [dbo].[OrderItems]
DROP CONSTRAINT [FK_OrderItems_Orders_New];

ALTER TABLE [dbo].[OrderItems]
ADD CONSTRAINT [FK_OrderItems_Orders_SetNull] FOREIGN KEY ([OrderId]) 
REFERENCES [dbo].[Orders]([Id]) ON DELETE SET NULL;

ALTER TABLE [dbo].[Products]
DROP CONSTRAINT [FK_Products_Categories];

ALTER TABLE [dbo].[Products]
ADD CONSTRAINT [FK_Products_Categories_NoAction] FOREIGN KEY ([CategoryId]) 
REFERENCES [dbo].[Categories]([Id]) ON DELETE NO ACTION ON UPDATE NO ACTION;

-- Migration Script 029: Change decimal to money and modify constraints
ALTER TABLE [dbo].[Orders]
ALTER COLUMN [TotalAmount] MONEY NOT NULL;

ALTER TABLE [dbo].[Orders]
ALTER COLUMN [UnitPrice] MONEY NOT NULL;

ALTER TABLE [dbo].[OrderItems]
ALTER COLUMN [UnitPrice] MONEY NOT NULL;

ALTER TABLE [dbo].[Users]
ALTER COLUMN [AccountBalance] MONEY NOT NULL;

ALTER TABLE [dbo].[Users]
ALTER COLUMN [CreditLimit] MONEY NULL;

-- Migration Script 030: Add multiple indexes with different options
CREATE INDEX [IX_Users_Registration_Active] ON [dbo].[Users]([RegistrationDate], [IsActive])
INCLUDE ([Name], [Email])
WHERE [IsActive] = 1;

CREATE INDEX [IX_Orders_Currency_Status] ON [dbo].[Orders]([Currency], [Status])
INCLUDE ([TotalAmount])
WHERE [Status] != 'Cancelled';

CREATE INDEX [IX_Products_Supplier_Manufacturer] ON [dbo].[Products]([SupplierId], [Manufacturer])
INCLUDE ([Name], [Price])
WHERE [SupplierId] IS NOT NULL;

-- Migration Script 031: Modify check constraints and add new ones
ALTER TABLE [dbo].[Orders]
DROP CONSTRAINT [CK_Orders_TotalAmount_Range];

ALTER TABLE [dbo].[Orders]
ADD CONSTRAINT [CK_Orders_TotalAmount_Valid] CHECK ([TotalAmount] >= 0 AND [TotalAmount] <= 5000000);

ALTER TABLE [dbo].[OrderItems]
DROP CONSTRAINT [CK_OrderItems_Quantity_Range];

ALTER TABLE [dbo].[OrderItems]
ADD CONSTRAINT [CK_OrderItems_Quantity_Valid] CHECK ([Quantity] > 0 AND [Quantity] <= 50000);

ALTER TABLE [dbo].[OrderItems]
ADD CONSTRAINT [CK_OrderItems_Discount] CHECK ([Discount] >= 0 AND [Discount] <= 100);

-- Migration Script 032: Change nvarchar columns and add indexes
ALTER TABLE [dbo].[Users]
ALTER COLUMN [Name] NVARCHAR(300) NOT NULL;

ALTER TABLE [dbo].[Products]
ALTER COLUMN [Name] NVARCHAR(1000) NOT NULL;

ALTER TABLE [dbo].[Categories]
ALTER COLUMN [Name] NVARCHAR(300) NOT NULL;

CREATE INDEX [IX_Users_Name_Search] ON [dbo].[Users]([Name])
INCLUDE ([Email], [IsActive])
WHERE [IsActive] = 1;

CREATE INDEX [IX_Products_Name_Search] ON [dbo].[Products]([Name])
INCLUDE ([SKU], [Price], [CategoryId])
WHERE [IsDiscontinued] = 0;

-- Migration Script 033: Modify datetime precision and add computed columns
ALTER TABLE [dbo].[Users]
ALTER COLUMN [CreatedAt] DATETIME2(7) NOT NULL;

ALTER TABLE [dbo].[Users]
ALTER COLUMN [LastLogin] DATETIME2(7) NULL;

ALTER TABLE [dbo].[Products]
ALTER COLUMN [CreatedAt] DATETIME2(7) NOT NULL;

ALTER TABLE [dbo].[Products]
ALTER COLUMN [ModifiedAt] DATETIME2(7) NULL;

ALTER TABLE [dbo].[Orders]
ADD [OrderYear] AS (YEAR([OrderDate])) PERSISTED,
    [OrderMonth] AS (MONTH([OrderDate])) PERSISTED;

-- Migration Script 034: Drop indexes and recreate with different filters
DROP INDEX [IX_Users_Email_Active] ON [dbo].[Users];
CREATE INDEX [IX_Users_Email_Verified] ON [dbo].[Users]([Email])
INCLUDE ([Name], [IsActive], [EmailVerifiedAt])
WHERE [IsActive] = 1 AND [EmailVerifiedAt] IS NOT NULL;

DROP INDEX [IX_Orders_Status_Date] ON [dbo].[Orders];
CREATE INDEX [IX_Orders_Completed] ON [dbo].[Orders]([OrderDate], [Status])
INCLUDE ([TotalAmount], [UserId], [Currency])
WHERE [Status] = 'Completed' AND [OrderDate] >= DATEADD(YEAR, -1, GETDATE());

-- Migration Script 035: Change column types and modify foreign keys
ALTER TABLE [dbo].[Users]
ALTER COLUMN [Email] NVARCHAR(1000) NOT NULL;

ALTER TABLE [dbo].[Products]
ALTER COLUMN [SKU] NVARCHAR(200) NOT NULL;

ALTER TABLE [dbo].[OrderItems]
DROP CONSTRAINT [FK_OrderItems_Products_New];

ALTER TABLE [dbo].[OrderItems]
ADD CONSTRAINT [FK_OrderItems_Products_Cascade] FOREIGN KEY ([ProductId]) 
REFERENCES [dbo].[Products]([Id]) ON DELETE CASCADE ON UPDATE CASCADE;

-- Migration Script 036: Add columns with defaults and modify existing
ALTER TABLE [dbo].[Users]
ADD [TwoFactorEnabled] BIT NOT NULL DEFAULT 0,
    [EmailNotifications] BIT NOT NULL DEFAULT 1,
    [SmsNotifications] BIT NOT NULL DEFAULT 0;

ALTER TABLE [dbo].[Orders]
ADD [Priority] INT NOT NULL DEFAULT 5,
    [EstimatedDelivery] DATE NULL,
    [ActualDelivery] DATE NULL;

ALTER TABLE [dbo].[Products]
ADD [Rating] DECIMAL(3,2) NULL,
    [ReviewCount] INT NOT NULL DEFAULT 0;

-- Migration Script 037: Modify decimal precision and add constraints
ALTER TABLE [dbo].[Products]
ALTER COLUMN [Price] MONEY NOT NULL;

ALTER TABLE [dbo].[Products]
ALTER COLUMN [Weight] DECIMAL(12,4) NULL;

ALTER TABLE [dbo].[Products]
ADD CONSTRAINT [CK_Products_Rating] CHECK ([Rating] IS NULL OR ([Rating] >= 0 AND [Rating] <= 5)),
    CONSTRAINT [CK_Products_ReviewCount] CHECK ([ReviewCount] >= 0);

ALTER TABLE [dbo].[Orders]
ADD CONSTRAINT [CK_Orders_Priority] CHECK ([Priority] >= 1 AND [Priority] <= 10);

-- Migration Script 038: Create functions and modify tables
CREATE FUNCTION [dbo].[CalculateOrderTotal](@OrderId INT)
RETURNS MONEY
AS
BEGIN
    DECLARE @Total MONEY;
    SELECT @Total = SUM([Quantity] * [UnitPrice] * (1 - [Discount] / 100))
    FROM [dbo].[OrderItems]
    WHERE [OrderId] = @OrderId;
    RETURN ISNULL(@Total, 0);
END;

ALTER TABLE [dbo].[Orders]
ADD [CalculatedTotal] AS ([dbo].[CalculateOrderTotal]([Id])) PERSISTED;

ALTER TABLE [dbo].[Users]
ADD [TotalOrders] INT NOT NULL DEFAULT 0,
    [TotalSpent] MONEY NOT NULL DEFAULT 0;

-- Migration Script 039: Drop and recreate constraints with different names
ALTER TABLE [dbo].[Products]
DROP CONSTRAINT [CK_Products_Price];

ALTER TABLE [dbo].[Products]
ADD CONSTRAINT [CK_Products_Price_Positive] CHECK ([Price] > 0);

ALTER TABLE [dbo].[Products]
DROP CONSTRAINT [CK_Products_Stock];

ALTER TABLE [dbo].[Products]
ADD CONSTRAINT [CK_Products_Stock_NonNegative] CHECK ([StockQuantity] >= 0);

ALTER TABLE [dbo].[Products]
DROP CONSTRAINT [CK_Products_ReorderLevel];

ALTER TABLE [dbo].[Products]
ADD CONSTRAINT [CK_Products_ReorderLevel_Positive] CHECK ([ReorderLevel] > 0);

-- Migration Script 040: Modify indexes with different include columns
DROP INDEX [IX_Users_Name_Search] ON [dbo].[Users];
CREATE INDEX [IX_Users_Name_Extended] ON [dbo].[Users]([Name])
INCLUDE ([Email], [IsActive], [CreatedAt], [LastLogin])
WHERE [IsActive] = 1;

DROP INDEX [IX_Products_Name_Search] ON [dbo].[Products];
CREATE INDEX [IX_Products_Name_Extended] ON [dbo].[Products]([Name])
INCLUDE ([SKU], [Price], [CategoryId], [StockQuantity], [Rating])
WHERE [IsDiscontinued] = 0;

-- Migration Script 041: Change date columns and add computed columns
ALTER TABLE [dbo].[Users]
ALTER COLUMN [RegistrationDate] DATETIME2 NULL;

ALTER TABLE [dbo].[Orders]
ALTER COLUMN [ShippedDate] DATETIME2 NULL;

ALTER TABLE [dbo].[Orders]
ALTER COLUMN [DeliveredDate] DATETIME2 NULL;

ALTER TABLE [dbo].[Orders]
ADD [ShippingDays] AS (CASE WHEN [ShippedDate] IS NOT NULL AND [DeliveredDate] IS NOT NULL 
    THEN DATEDIFF(DAY, [ShippedDate], [DeliveredDate]) ELSE NULL END) PERSISTED;

-- Migration Script 042: Modify foreign key cascade options
ALTER TABLE [dbo].[Orders]
DROP CONSTRAINT [FK_Orders_Users_Cascade];

ALTER TABLE [dbo].[Orders]
ADD CONSTRAINT [FK_Orders_Users_Restrict] FOREIGN KEY ([UserId]) 
REFERENCES [dbo].[Users]([Id]) ON DELETE NO ACTION ON UPDATE NO ACTION;

ALTER TABLE [dbo].[OrderItems]
DROP CONSTRAINT [FK_OrderItems_Orders_SetNull];

ALTER TABLE [dbo].[OrderItems]
ADD CONSTRAINT [FK_OrderItems_Orders_Restrict] FOREIGN KEY ([OrderId]) 
REFERENCES [dbo].[Orders]([Id]) ON DELETE NO ACTION;

-- Migration Script 043: Add unique indexes with filters and modify existing
DROP INDEX [UQ_Products_SKU_Unique] ON [dbo].[Products];
CREATE UNIQUE INDEX [UQ_Products_SKU_Active] ON [dbo].[Products]([SKU])
WHERE [IsDiscontinued] = 0;

ALTER TABLE [dbo].[Users]
ADD [Username] NVARCHAR(100) NULL;

CREATE UNIQUE INDEX [UQ_Users_Username] ON [dbo].[Users]([Username])
WHERE [Username] IS NOT NULL AND [IsActive] = 1;

-- Migration Script 044: Change money back to decimal with different precision
ALTER TABLE [dbo].[Orders]
ALTER COLUMN [TotalAmount] DECIMAL(28,8) NOT NULL;

ALTER TABLE [dbo].[Orders]
ALTER COLUMN [UnitPrice] DECIMAL(28,8) NOT NULL;

ALTER TABLE [dbo].[OrderItems]
ALTER COLUMN [UnitPrice] DECIMAL(28,8) NOT NULL;

ALTER TABLE [dbo].[Users]
ALTER COLUMN [AccountBalance] DECIMAL(28,8) NOT NULL;

ALTER TABLE [dbo].[Users]
ALTER COLUMN [CreditLimit] DECIMAL(28,8) NULL;

-- Migration Script 045: Modify check constraints and add indexes
ALTER TABLE [dbo].[Orders]
DROP CONSTRAINT [CK_Orders_TotalAmount_Valid];

ALTER TABLE [dbo].[Orders]
ADD CONSTRAINT [CK_Orders_TotalAmount_Max] CHECK ([TotalAmount] >= 0 AND [TotalAmount] <= 10000000);

ALTER TABLE [dbo].[OrderItems]
DROP CONSTRAINT [CK_OrderItems_Quantity_Valid];

ALTER TABLE [dbo].[OrderItems]
ADD CONSTRAINT [CK_OrderItems_Quantity_Max] CHECK ([Quantity] > 0 AND [Quantity] <= 100000);

CREATE INDEX [IX_Orders_TotalAmount] ON [dbo].[Orders]([TotalAmount])
INCLUDE ([OrderDate], [Status], [UserId])
WHERE [TotalAmount] > 1000;

-- Migration Script 046: Add columns with datetime2 and modify existing
ALTER TABLE [dbo].[Users]
ADD [PasswordChangedAt] DATETIME2 NULL,
    [AccountLockedUntil] DATETIME2 NULL,
    [TermsAcceptedAt] DATETIME2 NULL;

ALTER TABLE [dbo].[Products]
ADD [LastRestocked] DATETIME2 NULL,
    [LastSold] DATETIME2 NULL,
    [DiscontinuedAt] DATETIME2 NULL;

ALTER TABLE [dbo].[Orders]
ADD [CancelledAt] DATETIME2 NULL,
    [RefundedAt] DATETIME2 NULL;

-- Migration Script 047: Modify indexes with different filter conditions
DROP INDEX [IX_Users_Registration_Active] ON [dbo].[Users];
CREATE INDEX [IX_Users_Active_Recent] ON [dbo].[Users]([IsActive], [CreatedAt])
INCLUDE ([Name], [Email], [LastLogin])
WHERE [IsActive] = 1 AND [CreatedAt] >= DATEADD(YEAR, -2, GETDATE());

DROP INDEX [IX_Orders_Currency_Status] ON [dbo].[Orders];
CREATE INDEX [IX_Orders_Active_Currency] ON [dbo].[Orders]([Status], [Currency], [OrderDate])
INCLUDE ([TotalAmount])
WHERE [Status] IN ('Pending', 'Processing', 'Shipped', 'Delivered') 
    AND [CancelledAt] IS NULL;

-- Migration Script 048: Change nvarchar sizes multiple times
ALTER TABLE [dbo].[Users]
ALTER COLUMN [Username] NVARCHAR(150) NULL;

ALTER TABLE [dbo].[Users]
ALTER COLUMN [PreferredLanguage] NVARCHAR(50) NULL;

ALTER TABLE [dbo].[Orders]
ALTER COLUMN [Status] NVARCHAR(150) NOT NULL;

ALTER TABLE [dbo].[Products]
ALTER COLUMN [Manufacturer] NVARCHAR(1000) NULL;

ALTER TABLE [dbo].[Categories]
ALTER COLUMN [Description] NVARCHAR(2000) NULL;

-- Migration Script 049: Modify decimal precision and add computed columns
ALTER TABLE [dbo].[Products]
ALTER COLUMN [Price] DECIMAL(30,10) NOT NULL;

ALTER TABLE [dbo].[Products]
ALTER COLUMN [Weight] DECIMAL(15,5) NULL;

ALTER TABLE [dbo].[Products]
ALTER COLUMN [Rating] DECIMAL(5,3) NULL;

ALTER TABLE [dbo].[OrderItems]
ALTER COLUMN [Quantity] DECIMAL(15,4) NOT NULL;

ALTER TABLE [dbo].[OrderItems]
ALTER COLUMN [Discount] DECIMAL(7,4) NOT NULL;

-- Migration Script 050: Drop and recreate indexes with complex filters
DROP INDEX [IX_Products_Active_Stock] ON [dbo].[Products];
CREATE INDEX [IX_Products_Available] ON [dbo].[Products]([CategoryId], [IsDiscontinued], [StockQuantity])
INCLUDE ([Name], [Price], [SKU], [Rating])
WHERE [IsDiscontinued] = 0 AND [StockQuantity] > [ReorderLevel] AND [Rating] >= 3.0;

DROP INDEX [IX_Orders_Completed] ON [dbo].[Orders];
CREATE INDEX [IX_Orders_HighValue] ON [dbo].[Orders]([OrderDate], [TotalAmount])
INCLUDE ([UserId], [Status], [Currency])
WHERE [TotalAmount] > 5000 AND [Status] = 'Completed' AND [CancelledAt] IS NULL;

-- Migration Script 051: Modify foreign keys and add new relationships
CREATE TABLE [dbo].[Suppliers] (
    [Id] INT IDENTITY(1,1) PRIMARY KEY,
    [Name] NVARCHAR(200) NOT NULL,
    [ContactEmail] NVARCHAR(500) NULL,
    [Phone] NVARCHAR(50) NULL
);

ALTER TABLE [dbo].[Products]
DROP CONSTRAINT [FK_Products_Categories_NoAction];

ALTER TABLE [dbo].[Products]
ADD CONSTRAINT [FK_Products_Categories_Cascade] FOREIGN KEY ([CategoryId]) 
REFERENCES [dbo].[Categories]([Id]) ON DELETE CASCADE;

ALTER TABLE [dbo].[Products]
ADD CONSTRAINT [FK_Products_Suppliers] FOREIGN KEY ([SupplierId]) 
REFERENCES [dbo].[Suppliers]([Id]) ON DELETE SET NULL;

-- Migration Script 052: Change datetime columns and add indexes
ALTER TABLE [dbo].[Users]
ALTER COLUMN [DateOfBirth] DATE NULL;

ALTER TABLE [dbo].[Users]
ALTER COLUMN [RegistrationDate] DATE NULL;

ALTER TABLE [dbo].[Orders]
ALTER COLUMN [OrderDate] DATETIME2 NOT NULL;

ALTER TABLE [dbo].[Orders]
ALTER COLUMN [EstimatedDelivery] DATETIME2 NULL;

ALTER TABLE [dbo].[Orders]
ALTER COLUMN [ActualDelivery] DATETIME2 NULL;

CREATE INDEX [IX_Users_BirthDate] ON [dbo].[Users]([DateOfBirth])
INCLUDE ([Name], [IsActive])
WHERE [DateOfBirth] IS NOT NULL;

-- Migration Script 053: Modify check constraints with complex conditions
ALTER TABLE [dbo].[Orders]
DROP CONSTRAINT [CK_Orders_TotalAmount_Max];

ALTER TABLE [dbo].[Orders]
ADD CONSTRAINT [CK_Orders_TotalAmount_Complex] CHECK (
    ([Status] = 'Cancelled' AND [TotalAmount] >= 0) OR
    ([Status] != 'Cancelled' AND [TotalAmount] > 0 AND [TotalAmount] <= 10000000)
);

ALTER TABLE [dbo].[OrderItems]
DROP CONSTRAINT [CK_OrderItems_Quantity_Max];

ALTER TABLE [dbo].[OrderItems]
ADD CONSTRAINT [CK_OrderItems_Quantity_Complex] CHECK (
    [Quantity] > 0 AND [Quantity] <= 100000 AND
    ([Discount] = 0 OR [Quantity] * [UnitPrice] * [Discount] / 100 <= [UnitPrice] * [Quantity])
);

-- Migration Script 054: Add filtered unique indexes and modify existing
DROP INDEX [UQ_Users_Email_New] ON [dbo].[Users];
CREATE UNIQUE INDEX [UQ_Users_Email_Active] ON [dbo].[Users]([Email])
WHERE [IsActive] = 1 AND [EmailVerifiedAt] IS NOT NULL;

DROP INDEX [UQ_Users_Username] ON [dbo].[Users];
CREATE UNIQUE INDEX [UQ_Users_Username_Active] ON [dbo].[Users]([Username])
WHERE [Username] IS NOT NULL AND [IsActive] = 1 AND [AccountLockedUntil] IS NULL;

-- Migration Script 055: Change column types and add computed columns with functions
ALTER TABLE [dbo].[Users]
ALTER COLUMN [Email] NVARCHAR(MAX) NOT NULL;

ALTER TABLE [dbo].[Products]
ALTER COLUMN [Description] NVARCHAR(MAX) NULL;

ALTER TABLE [dbo].[Orders]
ALTER COLUMN [Notes] NVARCHAR(MAX) NULL;

ALTER TABLE [dbo].[Users]
ADD [EmailDomain] AS (SUBSTRING([Email], CHARINDEX('@', [Email]) + 1, LEN([Email]))) PERSISTED,
    [IsCorporateEmail] AS (CASE WHEN [Email] LIKE '%@company.com' THEN 1 ELSE 0 END) PERSISTED;

-- Migration Script 056: Modify indexes with multiple include columns
DROP INDEX [IX_Users_Email_Verified] ON [dbo].[Users];
CREATE INDEX [IX_Users_Email_Comprehensive] ON [dbo].[Users]([Email])
INCLUDE ([Name], [IsActive], [EmailVerifiedAt], [CreatedAt], [LastLogin], [AccountBalance])
WHERE [IsActive] = 1;

DROP INDEX [IX_Orders_TotalAmount] ON [dbo].[Orders];
CREATE INDEX [IX_Orders_TotalAmount_Comprehensive] ON [dbo].[Orders]([TotalAmount], [OrderDate])
INCLUDE ([UserId], [Status], [Currency], [CreatedAt], [UpdatedAt])
WHERE [TotalAmount] > 1000 AND [CancelledAt] IS NULL;

-- Migration Script 057: Drop columns and add new ones with different types
ALTER TABLE [dbo].[Users]
DROP COLUMN [MobilePhone],
    [WorkPhone],
    [HomePhone];

ALTER TABLE [dbo].[Users]
ADD [PrimaryPhone] NVARCHAR(30) NULL,
    [SecondaryPhone] NVARCHAR(30) NULL,
    [PhoneCountryCode] NVARCHAR(5) NULL;

ALTER TABLE [dbo].[Products]
DROP COLUMN [SupplierId];

ALTER TABLE [dbo].[Products]
ADD [SupplierName] NVARCHAR(300) NULL,
    [SupplierCode] NVARCHAR(100) NULL;

-- Migration Script 058: Modify decimal precision multiple columns
ALTER TABLE [dbo].[Orders]
ALTER COLUMN [TotalAmount] DECIMAL(32,10) NOT NULL;

ALTER TABLE [dbo].[Orders]
ALTER COLUMN [UnitPrice] DECIMAL(32,10) NOT NULL;

ALTER TABLE [dbo].[OrderItems]
ALTER COLUMN [UnitPrice] DECIMAL(32,10) NOT NULL;

ALTER TABLE [dbo].[OrderItems]
ALTER COLUMN [Quantity] DECIMAL(18,6) NOT NULL;

ALTER TABLE [dbo].[OrderItems]
ALTER COLUMN [Discount] DECIMAL(10,6) NOT NULL;

-- Migration Script 059: Change datetime precision and add indexes
ALTER TABLE [dbo].[Users]
ALTER COLUMN [CreatedAt] DATETIME2(7) NOT NULL;

ALTER TABLE [dbo].[Users]
ALTER COLUMN [LastLogin] DATETIME2(7) NULL;

ALTER TABLE [dbo].[Users]
ALTER COLUMN [EmailVerifiedAt] DATETIME2(7) NULL;

ALTER TABLE [dbo].[Orders]
ALTER COLUMN [CreatedAt] DATETIME2(7) NOT NULL;

ALTER TABLE [dbo].[Orders]
ALTER COLUMN [UpdatedAt] DATETIME2(7) NULL;

CREATE INDEX [IX_Users_Activity] ON [dbo].[Users]([LastLogin], [IsActive])
INCLUDE ([Name], [Email])
WHERE [LastLogin] >= DATEADD(MONTH, -6, GETDATE()) AND [IsActive] = 1;

-- Migration Script 060: Complex migration with multiple operations
ALTER TABLE [dbo].[Users]
ALTER COLUMN [Name] NVARCHAR(500) NOT NULL;

ALTER TABLE [dbo].[Users]
ALTER COLUMN [Email] NVARCHAR(MAX) NOT NULL;

ALTER TABLE [dbo].[Products]
ALTER COLUMN [Name] NVARCHAR(MAX) NOT NULL;

ALTER TABLE [dbo].[Products]
ALTER COLUMN [SKU] NVARCHAR(300) NOT NULL;

ALTER TABLE [dbo].[Categories]
ALTER COLUMN [Name] NVARCHAR(500) NOT NULL;

DROP INDEX [IX_Users_Email_Comprehensive] ON [dbo].[Users];
CREATE INDEX [IX_Users_Email_Final] ON [dbo].[Users]([Email])
INCLUDE ([Name], [IsActive], [CreatedAt], [LastLogin], [AccountBalance], [TotalOrders], [TotalSpent])
WHERE [IsActive] = 1 AND [EmailVerifiedAt] IS NOT NULL;

DROP INDEX [IX_Products_Available] ON [dbo].[Products];
CREATE INDEX [IX_Products_Final] ON [dbo].[Products]([CategoryId], [IsDiscontinued], [StockQuantity], [Rating])
INCLUDE ([Name], [Price], [SKU], [Description], [Manufacturer], [ReviewCount])
WHERE [IsDiscontinued] = 0 AND [StockQuantity] > [ReorderLevel];
