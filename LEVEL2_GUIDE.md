# Level 2 prep guide

## Key concepts to explain clearly

1. Why the service layer owns the rules
   - The repository layer should only persist and query data.
   - Business rules such as borrowing limits, waitlist conflicts, and fee caps belong in the service layer.

2. Why a virtual clock matters
   - The library rules depend on a single, deterministic clock.
   - Using a database-backed clock makes tests and simulations predictable.

3. How the reservation flow works
   - A copy becomes reserved when a waitlisted member reaches the front of the queue.
   - The reserved member has three days to collect it.
   - If the window expires, the reservation is cancelled and the next waitlisted member can take it.

4. How the late-fee cap works
   - The charge is capped at the replacement cost of the book.
   - This avoids fees that exceed the cost of replacing the title.

## Sample interview answers

### Q: Why did you model copies separately from books?
A: Books represent titles, while copies represent physical inventory. That separation lets the system track availability, reservations, damage, and loss at the copy level without conflating them with the title-level catalog.

### Q: What happens when a member is downgraded to a lower tier?
A: The current implementation blocks new borrowing or renewal if the member currently has more active loans than the new tier allows. That makes the rule explicit and prevents the system from creating invalid state.

### Q: Why not let any member borrow when there is a waitlist?
A: The rules require that waitlisted members get first access when a copy becomes available. The service blocks non-waitlisted borrowing in that case so the queue is respected.

### Q: How did you handle concurrent usage?
A: The app uses transactional service methods and a single persistence store so critical state changes such as borrowing, returning, and reservation updates happen atomically. A production system would also need optimistic locking or row-level locks for higher contention.
