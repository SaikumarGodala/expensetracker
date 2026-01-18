import json, sys, re

FILE = r"c:/Users/godha/AndroidStudioProjects/ExpenseTracker/transactions_unique_corrected.jsonl"

allowed_types = {"INVALID","INCOME","EXPENSE","TRANSFER","LIABILITY","INVESTMENT"}
allowed_categories = {
    "Food & Dining","Groceries","Fuel","Shopping","Transport","Recharge","Insurance","Investment",
    "Bills & Utilities","Health & Fitness","Transfer","Salary","Credit Card Payment","Cash","Other"
}

issues = []
with open(FILE, 'r', encoding='utf-8') as f:
    for i, line in enumerate(f, 1):
        try:
            rec = json.loads(line)
        except json.JSONDecodeError:
            issues.append((i, "Invalid JSON"))
            continue
        t = rec.get('type')
        c = rec.get('category')
        cp = rec.get('counterparty')
        # type check
        if t not in allowed_types:
            issues.append((i, f"Unexpected type: {t}"))
        # category check
        if c not in allowed_categories:
            issues.append((i, f"Unexpected category: {c}"))
        # counterparty check – ambiguous if empty or whitespace
        if cp is None:
            issues.append((i, "Missing counterparty"))
        elif isinstance(cp, str) and cp.strip() == "":
            issues.append((i, "Empty counterparty"))
        # optional: flag counterparty that looks like a raw message fragment (contains "..." or "\n")
        if isinstance(cp, str) and ("..." in cp or "\n" in cp):
            issues.append((i, f"Ambiguous counterparty: {cp}"))

# Summarize
print(f"Total lines scanned: {i}")
print(f"Total issues found: {len(issues)}")
for line_no, msg in issues[:100]:  # limit output
    print(f"Line {line_no}: {msg}")
if len(issues) > 100:
    print(f"... and {len(issues)-100} more issues")
