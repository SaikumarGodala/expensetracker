import json
import collections

log_path = r"c:\Users\godha\AndroidStudioProjects\ExpenseTracker\app\src\main\java\com\saikumar\expensetracker\log_batch_1768715043721_5780ea5a.jsonl.json"

transactions = []

try:
    with open(log_path, 'r', encoding='utf-8') as f:
        for line in f:
            if line.strip():
                try:
                    data = json.loads(line)
                    transactions.append(data)
                except json.JSONDecodeError:
                    pass
except FileNotFoundError:
    print(f"File not found: {log_path}")
    exit(1)

print(f"Loaded {len(transactions)} transactions")

# Group by amount
by_amount = collections.defaultdict(list)
for t in transactions:
    amt = t.get('rawInput', {}).get('amount')
    if amt:
        by_amount[amt].append(t)

pairs_found = 0
for amt, txns in by_amount.items():
    if len(txns) < 2:
        continue
        
    # Check for debits vs credits
    debits = [t for t in txns if t.get('finalDecision', {}).get('transactionType') in ['EXPENSE', 'TRANSFER', 'INVESTMENT_OUTFLOW', 'LIABILITY_PAYMENT'] or getattr(t, 'rawInput', {}).get('direction') == 'DEBIT']
    credits = [t for t in txns if t.get('finalDecision', {}).get('transactionType') in ['INCOME', 'REFUND', 'TRANSFER'] or getattr(t, 'rawInput', {}).get('direction') == 'CREDIT']
    
    # Simple check for any potential pair
    for d in debits:
        d_sender = d.get('rawInput', {}).get('sender')
        d_time = d.get('timestamp')
        
        for c in credits:
            if c['transactionId'] == d['transactionId']:
                continue
                
            c_sender = c.get('rawInput', {}).get('sender')
            c_time = c.get('timestamp')
            
            time_diff = abs(c_time - d_time)
            
            # Check 48h (172800000 ms)
            if time_diff <= 172800000:
                print(f"POTENTIAL PAIR FOUND: Amount={amt}")
                print(f"  Debit: {d_sender} at {d_time} ({d['finalDecision']['transactionType']})")
                print(f"  Credit: {c_sender} at {c_time} ({c['finalDecision']['transactionType']})")
                
                if d_sender == c_sender:
                    print(f"  WARNING: Same Sender {d_sender} - would be skipped by logic")
                else:
                    print(f"  DIFFERENT SENDER - SHOULD PAIR")
                pairs_found += 1

print(f"Total potential pairs found: {pairs_found}")
