1) Goals & Non-goals
Goals
- Server-authoritative Fabric 1.21.1 economy system with shops + auction house, designed for high cohesion and low coupling.
- Supports vanilla and modded items with stable, explicit item identity including components/NBT.
- Strong persistence and crash-safe monetary operations using SQLite by default and optional PostgreSQL.
- Modular architecture with a core JVM domain that is independent of Minecraft classes.
- Integration hooks for other mods: events + API for balances, pricing, listings, and transactions.
- Robust auditing, permissions, and anti-exploit protections.

Non-goals
- Client-only features or client-side authoritative logic.
- Real-money transactions, web store, or cross-server economy syncing.
- In-game economy simulation beyond pluggable pricing strategy.

2) Feature list (MVP -> V1 -> V2), with acceptance criteria
MVP
- Currency accounts: per-player with ledger and atomic balance updates.
- Admin shops: fixed prices, infinite stock option, category list, search.
- Auction house: list, bid, buyout, expire, cancel.
- Offline deliveries for items and money.
- SQLite persistence; migration framework for schema updates.
- LuckPerms-compatible permissions via abstraction.
- ItemKey identity with stable serialization and matching modes.

Acceptance criteria (MVP)
- All monetary operations are atomic and crash-safe, verified by transaction logs.
- Admin shop purchases/sales succeed for items with components/NBT.
- Auction buyout is atomic and cannot double-spend or double-deliver.
- Offline delivery is reliable across server restart.
- Unit tests can run without Minecraft, using core domain only.

V1
- Player shops with container-backed stock and permissions.
- Taxes and fees (configurable per shop and auction).
- Basic GUI flows for shop browse/search and auction browse.
- PostgreSQL support with identical functionality.
- Rate-limited event hooks for other mods.

Acceptance criteria (V1)
- Player shop stock is consistent with container; no dupes on buy/sell.
- Fees/taxes are applied and logged as separate ledger entries.
- GUI searches across categories and item metadata.
- PostgreSQL schema runs with identical tests to SQLite.

V2
- Multi-currency support with exchange rates.
- Optional dynamic pricing strategy module (supply/demand).
- Shop bundles/recipes and bulk listing.
- Cross-player bank accounts (teams/guilds) with shared permissions.

Acceptance criteria (V2)
- Multiple currencies can be configured and queried by API.
- Dynamic pricing strategy can be swapped without core changes.
- Team bank transactions enforce member permissions.

3) Architecture (layers + modules) and why it’s low-coupling/high-cohesion
Layers
- Core domain (pure JVM): entities, value objects, policies, and services. No Minecraft classes.
- Application layer: orchestrates use cases, transactions, validation, and event publication.
- Infrastructure: DB repositories, serialization, scheduling, caching, messaging.
- Fabric adapter: Minecraft-specific ports (player inventories, item stacks, GUI, permissions).

Suggested package/module breakdown
- com.yanlai.economy.core
  - domain: Account, Currency, ItemKey, ShopOffer, AuctionListing, AuctionBid, Delivery, LedgerEntry
  - policy: PricingStrategy, FeePolicy, PermissionPolicy
  - service: BalanceService, ShopService, AuctionService, DeliveryService
- com.yanlai.economy.app
  - usecase: BuyFromShop, SellToShop, CreateListing, PlaceBid, Buyout, ClaimDelivery, AdminAdjust
  - tx: TransactionCoordinator
  - event: EventBus, EventTypes
- com.yanlai.economy.infra
  - db: DataSourceFactory, SqlDialect, Migrations
  - repo: AccountRepo, ShopRepo, AuctionRepo, DeliveryRepo, LedgerRepo, LockRepo
  - cache: ItemKeyCache, PricingCache
  - scheduler: ExpirationWorker
- com.yanlai.economy.fabric
  - adapter: FabricItemAdapter, FabricInventoryPort, FabricPermissionPort
  - gui: ShopScreenHandler, AuctionScreenHandler
  - command: EconomyCommands
  - event: FabricEventBridge

Why low-coupling/high-cohesion
- Domain logic depends only on interfaces (ports) defined in core/app.
- Fabric adapter implements ports and translates between Minecraft and domain.
- Infrastructure is isolated: DB and scheduling are behind repos and workers.
- Each service owns a single responsibility with strong boundaries.

4) Data model (schemas + serialization formats)
Serialization formats
- ItemKey serialized as a stable, canonical JSON (or NBT) and hashed. Stored as JSON + hash string.
- Money amounts stored as integer minor units (long) per currency.
- Timestamps stored as epoch millis (long).

ItemKey serialization
- Canonical JSON structure with sorted keys; stable component/NBT serialization.
- Hash: SHA-256 of canonical JSON. Used as index and equality check.

5) Public API design (interfaces/events) for other mods to integrate
Key interfaces (pseudocode)
- interface EconomyApi
  - Account getAccount(UUID playerId)
  - long getBalance(UUID accountId, String currencyId)
  - Result debit(UUID accountId, String currencyId, long amount, String reason)
  - Result credit(UUID accountId, String currencyId, long amount, String reason)
  - PriceQuote priceCheck(ItemKey key, long count, ShopId shopId)
  - AuctionListing createListing(UUID sellerId, ItemKey key, long count, Money startingPrice, Money buyout, long expiresAt)
  - Result placeBid(UUID bidderId, ListingId listingId, Money amount)
  - Result buyout(UUID buyerId, ListingId listingId)

- interface ItemKeyFactory
  - ItemKey fromItemStack(Object minecraftItemStack)
  - ItemKey fromSerialized(String json)

- interface PermissionPort
  - boolean hasPermission(UUID playerId, String node)

Events
- BalanceChanged(accountId, currencyId, delta, reason, txId)
- ShopTransactionCreated(offerId, buyerId, itemKeyHash, count, total, txId)
- AuctionListingCreated(listingId, sellerId, itemKeyHash)
- AuctionBidPlaced(listingId, bidderId, amount)
- AuctionListingExpired(listingId)
- DeliveryCreated(deliveryId, ownerId, type)

6) Key flows
Buy/sell (shop)
- Request -> usecase validates permission -> load offer -> price -> reserve inventory if stocked -> ledger debit/credit -> delivery created if player offline or inventory full -> commit -> events

Auction create/bid/buyout/cancel/expire
- Create: validate item removal from seller inventory on server thread, then persist listing and delivery of item into escrow (virtual). Ledger entry for listing fee.
- Bid: transactional row lock on listing, verify still open, verify bid increment, escrow bidder funds (reserved balance), release prior bidder reservation, update listing.
- Buyout: transactional lock, verify open, charge buyer, release escrow to seller (less fees), mark listing closed, create item delivery to buyer.
- Cancel: seller permission + only if no bids; return item to seller delivery.
- Expire: worker finds expired listings, locks row, closes listing, returns item to seller delivery, releases reserved bids.

Payout/delivery
- DeliveryService attempts immediate insertion if online; otherwise persists delivery with status PENDING and retry schedule.

Admin actions
- Direct balance adjustments with reason codes; always logged as ledger entries.

7) Concurrency & performance plan
- All DB operations via async worker threads; main thread only for inventory mutations.
- Use a TransactionCoordinator to ensure DB transaction boundaries and lock scope.
- ItemKey caching for frequently accessed item hashes.
- Use batched queries for market views, with pagination and indexes on itemKeyHash, price, expiresAt.
- Tick-safe inventory mutation: perform inventory changes on server thread via scheduled task; transaction only commits after successful mutation or compensates.

SQLite vs PostgreSQL
- SQLite: use immediate transactions, rely on serialized writes; single writer, use busy timeout.
- PostgreSQL: row-level locking with SELECT ... FOR UPDATE; use repeatable read for auction operations.

8) Security/anti-exploit plan
- All transactions use atomic DB transactions and ledger entries.
- Escrowed funds prevent fake bids and race conditions.
- Permission checks on all sensitive actions, abstracted for LuckPerms.
- Idempotent delivery claiming with status and versioning.
- Audit log for every transaction with reason, source, target, amount, item hash.
- Server thread inventory mutation with double-check removal counts to prevent dupes.

9) Config system & extensibility
- Config formats: JSON5 or HOCON with schema validation.
- Pricing rules: fixed pricing module; dynamic pricing as pluggable PricingStrategy.
- Taxes: per shop, per auction category, per currency.
- Blacklists/whitelists: item hashes, registry ids, or tags.
- Categories: by tag or explicit item list.
- Multi-currency enabled via config, with default currency.

10) GUI/UX plan (admin panels + player screens), plus command surface
Player
- Shop browser with categories, search, sort by price.
- Auction browser with filters, listing detail, bid/buyout.
- Delivery inbox screen with claim buttons.

Admin
- Shop editor GUI with offer creation, price edit, stock mode toggle.
- Auction admin panel for force-cancel, refund, or ban items.

Commands
- /eco balance, /eco pay, /eco admin set/add/sub
- /shop create, /shop edit, /shop open
- /ah list, /ah bid, /ah buyout, /ah cancel

11) Testing strategy (unit/integration/load), plus failure-mode handling
- Unit tests for core domain: ItemKey, pricing, fee calculations, auction rules.
- Integration tests for DB repositories with SQLite and PostgreSQL.
- Load tests for auction listing queries and bid contention.
- Failure-mode handling: DB retries for transient errors, safe rollback, delivery retry queue.

12) Concrete implementation checklist with milestones
Milestone 1: Core domain + DB
- Define ItemKey, Money, Account, LedgerEntry
- Implement SQLite schema + migrations
- Implement BalanceService and Ledger

Milestone 2: Shop system
- Admin shop offers, price, buy/sell
- Shop repository and usecases
- GUI shell for browsing and buying

Milestone 3: Auction system
- Listings, bids, buyout, expiration worker
- Escrow balance model
- Auction GUI

Milestone 4: Delivery + permissions
- Delivery service and persistence
- Permission abstraction with LuckPerms bridge

Milestone 5: API + events
- External API with events
- Integration tests and docs

Detailed requirements

Item identity (ItemKey)
- Fields: registryId (namespace:path), componentsJson (canonical), tagDataHash, fuzzyFlags (bitmask), itemHash (sha256), version.
- Count is stored separately from key.
- Components/NBT snapshot uses Mojang DataFixerUpper codec for ItemStack to NBT, then canonicalizes to JSON with sorted keys.
- Fuzzy matching: flags like IGNORE_DAMAGE, IGNORE_CUSTOM_NAME, IGNORE_LORE, IGNORE_ENCHANTS, IGNORE_COMPONENTS.
- Comparison uses hash + structural compare if needed; fuzzy matching strips selected fields before hashing.
- Serialized format: canonical JSON with sorted keys; stored in DB and used as payload in logs.

Tick-safe inventory mutation
- Use a FabricInventoryPort with methods runOnServerThread and mutateInventory.
- For sell/list: schedule removal task, verify exact item match by ItemKey, remove count, then persist listing/offer or credit funds.
- For buy/delivery: schedule insertion task, attempt insert, if full create delivery record and notify.
- Always re-validate ItemKey and counts on the server thread before finalizing.

DB schema (SQL-ish)
accounts
- account_id TEXT PK
- owner_uuid TEXT
- type TEXT (PLAYER/TEAM/BANK)
- created_at INTEGER

account_balances
- account_id TEXT FK accounts
- currency_id TEXT
- balance INTEGER
- reserved INTEGER
- version INTEGER
- PRIMARY KEY (account_id, currency_id)

transactions (ledger)
- tx_id TEXT PK
- ts INTEGER
- source_account TEXT
- target_account TEXT
- currency_id TEXT
- amount INTEGER
- reason TEXT
- item_hash TEXT
- meta_json TEXT

shop_offers
- offer_id TEXT PK
- shop_id TEXT
- item_hash TEXT
- item_json TEXT
- price INTEGER
- stock INTEGER
- infinite_stock INTEGER
- buy_enabled INTEGER
- sell_enabled INTEGER
- category TEXT
- version INTEGER

auction_listings
- listing_id TEXT PK
- seller_account TEXT
- item_hash TEXT
- item_json TEXT
- count INTEGER
- starting_price INTEGER
- buyout_price INTEGER
- bid_increment INTEGER
- created_at INTEGER
- expires_at INTEGER
- status TEXT (OPEN/CLOSED/EXPIRED/CANCELLED)
- version INTEGER

auction_bids
- bid_id TEXT PK
- listing_id TEXT
- bidder_account TEXT
- amount INTEGER
- created_at INTEGER
- status TEXT (ACTIVE/OUTBID/REFUNDED)

deliveries
- delivery_id TEXT PK
- owner_account TEXT
- type TEXT (ITEM/MONEY)
- item_hash TEXT
- item_json TEXT
- count INTEGER
- currency_id TEXT
- amount INTEGER
- status TEXT (PENDING/CLAIMED/FAILED)
- created_at INTEGER
- last_attempt INTEGER

locks
- lock_key TEXT PK
- locked_by TEXT
- locked_at INTEGER
- expires_at INTEGER

schema_version
- version INTEGER
- applied_at INTEGER

Minimal migration approach
- schema_version table with incremental migration scripts.
- On startup, apply pending migrations within a DB transaction.

Edge cases and handling (>=25)
1. Buyer inventory full -> create delivery record for items.
2. Seller inventory lacks item count at listing -> fail before listing creation.
3. Listing expires while bid is being placed -> bid transaction detects status and fails.
4. Double buyout attempts -> row lock + status check prevents second buyer.
5. Bidder disconnects mid-bid -> transaction completes; no dependence on session.
6. Negative amount input -> reject at validation layer.
7. Currency mismatch -> reject transaction and log reason.
8. Admin shop infinite stock -> no inventory removal required.
9. Player shop container moved -> fail with error and log.
10. Auction buyout at same time as expire worker -> row lock and status check.
11. Server crash after inventory removal before DB commit -> rollback and restore on next tick from pending task journal.
12. Server crash after DB commit before inventory removal -> compensating task to remove or refund.
13. Delivery claim spam -> idempotent status update with version check.
14. Permission changes mid-transaction -> re-check before commit for sensitive actions.
15. DB busy in SQLite -> retry with backoff.
16. Postgres deadlock -> retry transaction.
17. ItemKey fuzzy match used to sell wrong item -> use exact match unless shop config allows fuzzy.
18. Item with custom data updated by mod -> new ItemKey hash, treated as different item.
19. Auction listing with zero buyout -> only bids allowed.
20. Bid increment too small -> reject per listing rules.
21. Reserved funds for previous bid not released -> ensure escrow release in same transaction.
22. Player tries to cancel listing with bids -> disallow unless admin permission.
23. Tax rate over 100% -> clamp or reject config at load.
24. Overflow in balance math -> use long and validate bounds.
25. Item removal duplicates -> exact ItemKey + count check and server thread mutation.
26. Shop stock negative due to race -> version check + atomic update.
27. Auction listing created with expired time -> reject on validation.
28. Delivery for money with missing currency -> reject and log.
29. Data migration failure -> halt startup with clear message, no partial state.
30. Malformed ItemKey JSON -> reject and log, do not crash.

Notes
- Default to single currency "coins" with minor units.
- All timestamps in UTC epoch millis.
- Events are emitted after DB commit for consistency.
