package dev.nothingitis.bredcapexempt;

/**
 * Duck interface implemented onto {@code Animal} by {@code AnimalMixin}: carries
 * BredExempt's own marker so we can prove WE made an animal persistent (and why),
 * instead of inferring it from the generic PersistenceRequired flag.
 */
public interface BredExemptMarked {
    /** @return the marking reason ({@code breeding_offspring}, {@code breeding_parent},
     *          {@code thrown_egg}), or {@code null} if BredExempt never marked this animal. */
    String bredexempt$getReason();

    void bredexempt$setReason(String reason);
}
