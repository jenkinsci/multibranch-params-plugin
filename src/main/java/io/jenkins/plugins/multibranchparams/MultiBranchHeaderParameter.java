package io.jenkins.plugins.multibranchparams;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.EnvVars;
import hudson.Extension;
import hudson.model.ParameterDefinition;
import hudson.model.ParameterValue;
import hudson.model.Run;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

/**
 * A non-functional {@link ParameterDefinition} that renders a visual header
 * on the "Build with Parameters" page, clearly marking which parameters were
 * injected by the Multibranch Params plugin.
 *
 * <p>This class is never shown in the "Add Parameter" dropdown — it is only
 * inserted programmatically by {@link ParameterizedBranchProperty}.
 * Its {@link #createValue} methods return {@code null} so it contributes
 * nothing to the build's {@code ParametersAction}.
 */
public class MultiBranchHeaderParameter extends ParameterDefinition {

    /** Internal name — unlikely to clash with real parameter names. */
    static final String INTERNAL_NAME = "___multibranch_params_header___";

    @DataBoundConstructor
    public MultiBranchHeaderParameter() {
        super(INTERNAL_NAME, "");
    }

    // -------------------------------------------------------------------------
    // ParameterDefinition contract — all no-ops
    // -------------------------------------------------------------------------

    @CheckForNull
    @Override
    public ParameterValue createValue(StaplerRequest req, JSONObject jo) {
        return null;
    }

    @CheckForNull
    @Override
    public ParameterValue createValue(StaplerRequest req) {
        return null;
    }

    @CheckForNull
    @Override
    public ParameterValue getDefaultParameterValue() {
        return null;
    }

    // -------------------------------------------------------------------------
    // Descriptor
    // -------------------------------------------------------------------------

    /** Descriptor for {@link MultiBranchHeaderParameter}. */
    @Extension
    public static final class DescriptorImpl extends ParameterDescriptor {

        @NonNull
        @Override
        public String getDisplayName() {
            return "Multibranch Params Header (internal)";
        }

        /**
         * Overridden because the default {@code getViewPage} implementation uses
         * the Stapler/core classloader, which cannot see resources bundled inside
         * plugin jars. Using the plugin class's own classloader ensures the
         * {@code index.jelly} view is found at runtime.
         */
        @Override
        public String getValuePage() {
            String name = MultiBranchHeaderParameter.class.getName().replace('.', '/') + "/index.jelly";
            return MultiBranchHeaderParameter.class.getClassLoader().getResource(name) != null
                    ? '/' + name
                    : null;
        }
    }

    // -------------------------------------------------------------------------
    // No-op ParameterValue — not used at build time but kept for completeness
    // -------------------------------------------------------------------------

    /**
     * A {@link ParameterValue} that injects nothing into the build environment.
     * Never actually stored in a {@code ParametersAction} since
     * {@link MultiBranchHeaderParameter#createValue} returns {@code null}.
     */
    public static final class MultiBranchHeaderValue extends ParameterValue {

        public MultiBranchHeaderValue() {
            super(INTERNAL_NAME);
        }

        @Override
        public Object getValue() {
            return "";
        }

        @Override
        public void buildEnvironment(Run<?, ?> build, EnvVars env) {
            // intentionally empty — never inject into build environment
        }
    }
}
