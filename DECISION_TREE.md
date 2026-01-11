## TRANSACTION NATURE DECISION TREE

```
START: Transaction SMS
    │
    ├─ Extract: Amount, Sender, Timestamp, Message Body
    │
    ├─ DIRECTION DETECTION
    │  ├─ Contains "debited" or "spent" or "paid" → isDebit = true
    │  ├─ Contains "credited" or "received" or "deposited" (and NOT isDebit) → isCredit = true
    │  └─ Neither → SKIP (invalid transaction)
    │
    └─── TRANSACTION NATURE RESOLVER ───────────────────────────────────────────
         │
         ├─ [LEVEL 1] PENDING Detection
         │  │
         │  └─ Match any of:
         │     ├─ "WILL BE DEBITED"
         │     ├─ "HAS REQUESTED MONEY"
         │     ├─ "PAYMENT REQUEST"
         │     ├─ "DUE BY"
         │     ├─ "STANDING INSTRUCTION"
         │     ├─ "RECURRING CHARGE"
         │     └─ "SUBSCRIPTION"
         │  │
         │  ├─ ✓ MATCHED → Nature: PENDING
         │  │            → Type: PENDING
         │  │            → Skip saving to DB
         │  │            → STOP
         │  │
         │  └─ ✗ No match → Continue to Level 2
         │
         ├─ [LEVEL 2] CREDIT CARD PAYMENT Detection
         │  │
         │  └─ All of:
         │     ├─ Destination = CREDIT_CARD (detected from account type)
         │     ├─ Contains any pattern:
         │     │  ├─ "PAYMENT.*RECEIVED.*CREDIT CARD" (regex)
         │     │  ├─ "PAYMENT.*CREDITED.*CREDIT CARD"
         │     │  ├─ "RECEIVED.*TOWARDS YOUR CREDIT CARD"
         │     │  ├─ "CREDITED TO.*CREDIT CARD"
         │     │  ├─ "RECEIVED PAYMENT"
         │     │  ├─ "BBPS"
         │     │  └─ Flexible: "PAYMENT" + "RECEIVED/CREDITED" + "CREDIT CARD"
         │     │
         │  ├─ ✓ MATCHED → Nature: CREDIT_CARD_PAYMENT
         │  │            → Type: LIABILITY_PAYMENT
         │  │            → Category: CC Payment (not income, not expense!)
         │  │            → STOP
         │  │
         │  └─ ✗ No match → Continue to Level 3
         │
         ├─ [LEVEL 3] CREDIT CARD SPEND Detection
         │  │
         │  └─ All of:
         │     ├─ isDebit = true (must be debit)
         │     ├─ Account Type = CREDIT_CARD
         │     └─ Contains: "SPENT", "PURCHASE", "SWIPE", "CARD TRANSACTION"
         │
         │  ├─ ✓ MATCHED → Nature: CREDIT_CARD_SPEND
         │  │            → Type: EXPENSE
         │  │            → Category: By merchant (Swiggy → Food, etc.)
         │  │            → STOP
         │  │
         │  └─ ✗ No match → Continue to Level 4
         │
         ├─ [LEVEL 4] SELF TRANSFER Detection
         │  │
         │  └─ Any of:
         │     ├─ Keyword match:
         │     │  ├─ "SELF"
         │     │  ├─ "OWN ACCOUNT"
         │     │  ├─ "YOUR OWN ACCOUNT"
         │     │  ├─ "TRANSFER BETWEEN YOUR ACCOUNTS"
         │     │  └─ "TRANSFER TO SELF"
         │     │
         │     ├─ Pattern match:
         │     │  ├─ "CREDITED FROM <UPI>@<PROVIDER>" (where UPI is known)
         │     │  ├─ "DEBITED TO <UPI>@<PROVIDER>" (where UPI is known)
         │     │  └─ "A/C XXXXX TRANSFER" patterns
         │
         │  ├─ ✓ MATCHED → Nature: SELF_TRANSFER
         │  │            → Type: TRANSFER
         │  │            → Category: Internal Transfer
         │  │            → STOP
         │  │
         │  └─ ✗ No match → Continue to Level 5
         │
         ├─ [LEVEL 5] INCOME Detection
         │  │
         │  └─ Requirements:
         │     ├─ isCredit = true (must be credit)
         │     ├─ NOT isDebit
         │     └─ Contains: "SALARY", "NEFT", "INTEREST", "DIVIDEND", "BONUS", "REFUND", "CASHBACK"
         │
         │  ├─ ✓ MATCHED → Nature: INCOME
         │  │            → Type: INCOME or CASHBACK (if cashback in message)
         │  │            → Category: Salary or Other Income
         │  │            → STOP
         │  │
         │  └─ ✗ No match → Continue to Level 6
         │
         └─ [LEVEL 6] EXPENSE Detection (Fallback)
            │
            └─ isDebit = true
            
            ├─ ✓ MATCHED → Nature: EXPENSE
            │            → Type: EXPENSE (or INVESTMENT_OUTFLOW if investment keywords)
            │            → Category: By merchant pattern
            │            → STOP
            │
            └─ ✓ FALLBACK → Nature: Based on direction
                           → Type: INCOME if isCredit, EXPENSE if isDebit
                           → Confidence: 50% (safety net only)

AFTER NATURE RESOLUTION:
    │
    ├─ [HARD INVARIANT CHECKS]
    │  ├─ If isDebit AND transactionType == INCOME → Force to EXPENSE
    │  ├─ If CC payment AND transactionType != LIABILITY_PAYMENT → Force to LIABILITY_PAYMENT
    │  └─ If PENDING → Skip saving to database
    │
    ├─ [LOG DECISION]
    │  ├─ Transaction Nature
    │  ├─ Matched Rule
    │  ├─ Confidence Score
    │  ├─ Skipped Rules
    │  └─ Rule Trace
    │
    └─ [SAVE TO DATABASE] (if not PENDING/IGNORE)
```

## Decision Table: When Each Rule Matches

| SMS Content | Direction | Level | Rule | Result |
|------------|-----------|-------|------|--------|
| "will be debited...Standing Instruction" | Debit | 1 | PENDING | Skip, not saved |
| "Payment received on credit card via BBPS" | Credit | 2 | CC_PAYMENT | LIABILITY_PAYMENT |
| "Spent INR 100 on Swiggy" | Debit | 3 | CC_SPEND | EXPENSE (Food) |
| "Credited from 9505458713@ybl" | Credit | 4 | SELF_TRANSFER | TRANSFER |
| "Credited with INR 50,000 salary" | Credit | 5 | INCOME | INCOME (Salary) |
| "Debited for electricity bill" | Debit | 6 | EXPENSE | EXPENSE (Utility) |
| "Credited with INR 100 (no reason)" | Credit | - | FALLBACK | INCOME (50% confidence) |

## Hard Invariants (Non-Negotiable)

```
┌─────────────────────────────────────────────────────────────┐
│ INVARIANT 1: No Debit Can Be Income                         │
├─────────────────────────────────────────────────────────────┤
│ If message.contains("debited")                              │
│    AND transactionType == INCOME                            │
│ Then FORCE transactionType = EXPENSE                        │
│                                                              │
│ Financial Logic: Debit = money out, Income = money in       │
│ These are mutually exclusive                                │
└─────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────┐
│ INVARIANT 2: CC Payments Are Liability Settlement           │
├─────────────────────────────────────────────────────────────┤
│ If message contains ("credit card" AND "received")          │
│    AND transactionType NOT IN (LIABILITY_PAYMENT, PENDING)  │
│ Then FORCE transactionType = LIABILITY_PAYMENT              │
│                                                              │
│ Financial Logic: Payment TO credit card settles liability,  │
│ NEVER expenses, NEVER income                                │
└─────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────┐
│ INVARIANT 3: Pending Transactions Don't Save                │
├─────────────────────────────────────────────────────────────┤
│ If transactionType IN (PENDING, IGNORE)                     │
│ Then DO NOT save to database                                │
│                                                              │
│ Financial Logic: Future debits are not actual transactions  │
└─────────────────────────────────────────────────────────────┘
```

## Problem Case Examples

### Case 1: ICICI CC Payment
```
Input: "Payment of Rs 5,296.00 has been received on your ICICI Bank Credit Card XX2008"

Direction Detection:
  ✓ Contains "received" (is credit keyword)
  → isCredit = true, isDebit = false

Nature Resolution:
  Level 1 (PENDING): No pending keywords → Skip
  Level 2 (CC_PAYMENT):
    ✓ "Payment" keyword found
    ✓ "received" keyword found
    ✓ "Credit Card" keyword found
    ✓ FLEXIBLE MATCH: payment + received + credit card
    → Nature: CREDIT_CARD_PAYMENT
    → STOP (don't evaluate Level 5 INCOME)

Output: LIABILITY_PAYMENT (correct!)
        Not INCOME ✓
```

### Case 2: Standing Instruction
```
Input: "Dear Customer, your payment of INR 79.00 for Amazon will be debited from your ICICI Bank Credit Card 0006, as per Standing Instructions"

Direction Detection:
  ✓ Contains "debited" (is debit keyword)
  ✓ Contains "will be" (future)
  → isDebit = true, isCredit = false

Nature Resolution:
  Level 1 (PENDING):
    ✓ "WILL BE DEBITED" matched
    ✓ "STANDING INSTRUCTION" matched
    → Nature: PENDING
    → STOP

Output: PENDING (not saved to DB) ✓
        Never becomes EXPENSE ✓
```

### Case 3: Bank Debit (Should Be EXPENSE)
```
Input: "ICICI Bank Acct XX294 debited for Rs 1.00 on 09-Jan-26"

Direction Detection:
  ✓ Contains "debited" (is debit keyword)
  → isDebit = true, isCredit = false

Nature Resolution:
  Level 1 (PENDING): No pending keywords → Skip
  Level 2 (CC_PAYMENT): No CC destination → Skip
  Level 3 (CC_SPEND): Not credit card, it's bank account → Skip
  Level 4 (SELF_TRANSFER): No self-transfer indicators → Skip
  Level 5 (INCOME): isCredit = false, skip (income is credit only) → Skip
  Level 6 (EXPENSE): isDebit = true → MATCHED
    → Nature: EXPENSE

Hard Invariants:
  ✓ isDebit = true, but transactionType = EXPENSE (not INCOME) → Pass

Output: EXPENSE (correct!) ✓
        NEVER INCOME ✓
```
