# Auction Workflow

Unowned property scans offer **BUY** or **AUCTION**. Auction starts after scanning the landing player, then navigates to `AuctionScreen`.

## Bidding

- Fixed **M20** increments via `PlaceAuctionBid`
- Jailed players are rejected by the engine
- Bids commit to `GameSession.auction`; timer state is app-only

## Timer

App-specific **30 second** countdown (`AuctionConfig.TIMER_SECONDS`). Timer resets on each bid. This is not claimed to match original hardware timing.

## Completion

- Timer expires with bids → `CompleteAuction`
- No bids → restart or leave unowned (`CancelAuction`)
- Insufficient winner funds → debt resolution (`DebtReason.PURCHASE`)

## App restart

Committed bids in `GameSession.auction` may be restored; timer restarts. Uncommitted in-progress UI is discarded per GR-SAVE-002.
