package io.jenkins.plugins.multibranchparams;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.DescriptorExtensionList;
import hudson.model.ParameterDefinition;
import jenkins.branch.BranchProperty;
import jenkins.branch.BranchPropertyStrategy;
import jenkins.branch.BranchPropertyStrategyDescriptor;
import jenkins.scm.api.SCMHead;
import hudson.util.ListBoxModel;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * A {@link BranchPropertyStrategy} that applies {@link ParameterizedBranchProperty}
 * selectively to branches based on a name regex pattern.
 *
 * <h3>Filter modes</h3>
 * <ul>
 *   <li>{@link FilterMode#ALL} – every branch receives the parameters (default).</li>
 *   <li>{@link FilterMode#INCLUDE_PATTERN} – only branches whose names match
 *       {@link #branchPattern} receive the parameters.</li>
 *   <li>{@link FilterMode#EXCLUDE_PATTERN} – all branches <em>except</em> those
 *       matching {@link #branchPattern} receive the parameters.</li>
 * </ul>
 *
 * <p>For the simple "all branches" use case you can instead just use the Branch API's
 * built-in {@code DefaultBranchPropertyStrategy} and attach
 * {@link ParameterizedBranchProperty} to it. This strategy is for advanced filtering.
 */
public class ParameterizedBranchPropertyStrategy extends BranchPropertyStrategy {

    private static final Logger LOGGER =
            Logger.getLogger(ParameterizedBranchPropertyStrategy.class.getName());

    // -------------------------------------------------------------------------
    // Filter mode enum
    // -------------------------------------------------------------------------

    /** Determines which branches receive the injected parameters. */
    public enum FilterMode {
        /** Every branch gets the parameters regardless of its name. */
        ALL,
        /** Only branches whose names fully match the pattern get the parameters. */
        INCLUDE_PATTERN,
        /** All branches whose names do NOT match the pattern get the parameters. */
        EXCLUDE_PATTERN
    }

    // -------------------------------------------------------------------------
    // Fields
    // -------------------------------------------------------------------------

    private FilterMode filterMode = FilterMode.ALL;

    /** Java regex matched against the full branch name when filterMode != ALL. */
    private String branchPattern = ".*";

    /** Parameters to inject when a branch matches. */
    private List<ParameterDefinition> parameterDefinitions = new ArrayList<>();

    /** Controls how scan-time injection interacts with Jenkinsfile-set parameters. */
    private ParameterPolicy parameterPolicy = ParameterPolicy.REPLACE;

    // -------------------------------------------------------------------------
    // Construction
    // -------------------------------------------------------------------------

    /** Required by Stapler for data binding. */
    @DataBoundConstructor
    public ParameterizedBranchPropertyStrategy() {
    }

    // -------------------------------------------------------------------------
    // Getters / Setters
    // -------------------------------------------------------------------------

    /** @return the active {@link FilterMode} */
    public FilterMode getFilterMode() {
        return filterMode;
    }

    @DataBoundSetter
    public void setFilterMode(FilterMode filterMode) {
        this.filterMode = filterMode != null ? filterMode : FilterMode.ALL;
    }

    /** @return the Java regex used for branch name matching */
    public String getBranchPattern() {
        return branchPattern;
    }

    @DataBoundSetter
    public void setBranchPattern(String branchPattern) {
        this.branchPattern = branchPattern != null ? branchPattern : ".*";
    }

    /** @return unmodifiable list of configured parameter definitions */
    public List<ParameterDefinition> getParameterDefinitions() {
        return Collections.unmodifiableList(parameterDefinitions);
    }

    @DataBoundSetter
    public void setParameterDefinitions(@NonNull List<ParameterDefinition> parameterDefinitions) {
        this.parameterDefinitions = new ArrayList<>(parameterDefinitions);
    }

    public ParameterPolicy getParameterPolicy() {
        return parameterPolicy;
    }

    @DataBoundSetter
    public void setParameterPolicy(@NonNull ParameterPolicy parameterPolicy) {
        this.parameterPolicy = parameterPolicy;
    }

    // -------------------------------------------------------------------------
    // Core: decide which properties apply for a given branch
    // -------------------------------------------------------------------------

    /**
     * Called by the Branch API for each discovered {@link SCMHead} (branch/PR/tag).
     * Returns the list of {@link BranchProperty} instances to apply to that branch's job.
     *
     * @param head the SCM head representing the branch
     * @return singleton list with a configured {@link ParameterizedBranchProperty},
     *         or an empty list if the branch is filtered out
     */
    @NonNull
    @Override
    public List<BranchProperty> getPropertiesFor(SCMHead head) {
        if (!branchMatches(head.getName())) {
            LOGGER.fine(() -> "Branch '" + head.getName()
                    + "' does not match filter – skipping parameter injection.");
            return Collections.emptyList();
        }

        ParameterizedBranchProperty property = new ParameterizedBranchProperty();
        property.setParameterDefinitions(new ArrayList<>(parameterDefinitions));
        property.setParameterPolicy(parameterPolicy);

        LOGGER.fine(() -> "Branch '" + head.getName()
                + "' matched – injecting " + parameterDefinitions.size() + " parameter(s).");
        return Collections.singletonList(property);
    }

    /**
     * Evaluates whether a branch name should receive the parameters.
     *
     * @param branchName the full name of the branch
     * @return {@code true} if this branch should get the parameters
     */
    private boolean branchMatches(String branchName) {
        if (filterMode == FilterMode.ALL) {
            return true;
        }

        boolean patternMatches;
        try {
            patternMatches = Pattern.compile(branchPattern).matcher(branchName).matches();
        } catch (PatternSyntaxException ex) {
            LOGGER.log(Level.WARNING,
                    "Invalid branch pattern ''{0}'' – treating as match-all. Error: {1}",
                    new Object[]{branchPattern, ex.getMessage()});
            patternMatches = true;
        }

        return filterMode == FilterMode.INCLUDE_PATTERN ? patternMatches : !patternMatches;
    }

    // -------------------------------------------------------------------------
    // Descriptor
    // -------------------------------------------------------------------------

    /** Jenkins extension descriptor for {@link ParameterizedBranchPropertyStrategy}. */
    @Extension
    @Symbol("parameterizedBranchStrategy")
    public static class DescriptorImpl extends BranchPropertyStrategyDescriptor {

        @NonNull
        @Override
        public String getDisplayName() {
            return "Parameterized Branch Strategy";
        }

        /**
         * Returns all known {@link ParameterDefinition} descriptors for the UI dropdown.
         *
         * @return list of parameter descriptors
         */
        public List<ParameterDefinition.ParameterDescriptor> getParameterDescriptors() {
            return ParameterDefinition.all().stream()
                    .filter(d -> !(d instanceof MultiBranchHeaderParameter.DescriptorImpl))
                    .collect(Collectors.toList());
        }

        /**
         * Populates the filter mode dropdown with human-readable labels.
         * Using {@link ListBoxModel} keeps the submitted value as the enum
         * constant name (e.g. {@code "ALL"}), which {@code Enum.valueOf()} can
         * bind back correctly, while showing a friendly label in the UI.
         *
         * @return list-box model for the filterMode select
         */
        public ListBoxModel doFillFilterModeItems() {
            ListBoxModel model = new ListBoxModel();
            model.add("All branches", FilterMode.ALL.name());
            model.add("Only branches matching pattern", FilterMode.INCLUDE_PATTERN.name());
            model.add("All branches except those matching pattern", FilterMode.EXCLUDE_PATTERN.name());
            return model;
        }

        public ListBoxModel doFillParameterPolicyItems() {
            ListBoxModel model = new ListBoxModel();
            model.add("Always replace (plugin params override Jenkinsfile)", ParameterPolicy.REPLACE.name());
            model.add("Merge (keep Jenkinsfile params, plugin wins on conflict)", ParameterPolicy.MERGE.name());
            model.add("Skip if Jenkinsfile owns params (inject only until first Jenkinsfile build)", ParameterPolicy.SKIP_IF_JENKINSFILE.name());
            return model;
        }
    }
}
