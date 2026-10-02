package edu.cit.valendez.tiangge;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for FeedCursor.
 * Package-private to enforce module isolation.
 */
@Repository
interface FeedCursorRepository extends JpaRepository<FeedCursor, Long> {
}
