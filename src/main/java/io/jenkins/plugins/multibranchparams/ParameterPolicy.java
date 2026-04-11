package io.jenkins.plugins.multibranchparams;

/**
 * Controls how the Multibranch Params plugin interacts with parameters that
 * a Jenkinsfile may declare via its own {@code properties([parameters([...])])} block.
 *
 * <p>The plugin injects parameters at <em>scan time</em>; a Jenkinsfile's
 * {@code properties()} call runs at <em>build time</em> and can overwrite that.
 * This enum lets you choose the right trade-off for your pipeline.
 *
 * <table>
 *   <caption>Parameter policy behaviour</caption>
 *   <tr><th>Mode</th><th>At scan time</th><th>After a Jenkinsfile build runs</th></tr>
 *   <tr><td>REPLACE</td>
 *       <td>Always inject plugin params only, ignoring whatever is in the Jenkinsfile.</td>
 *       <td>Jenkinsfile can overwrite at build time; next scan restores plugin params.</td></tr>
 *   <tr><td>MERGE_PLUGIN_WINS</td>
 *       <td>Combine both; on a name conflict the plugin's definition takes precedence.</td>
 *       <td>Jenkinsfile can overwrite; next scan merges again.</td></tr>
 *   <tr><td>MERGE_JENKINSFILE_WINS</td>
 *       <td>Combine both; on a name conflict the Jenkinsfile's definition is kept.</td>
 *       <td>Jenkinsfile can overwrite; next scan merges again.</td></tr>
 *   <tr><td>SKIP_IF_JENKINSFILE</td>
 *       <td>Inject only if the Jenkinsfile has not yet defined its own params.</td>
 *       <td>Once Jenkinsfile has set params, subsequent scans leave them untouched.</td></tr>
 * </table>
 */
public enum ParameterPolicy {

    /**
     * Always inject plugin-defined parameters at scan time, replacing whatever
     * the job currently has (including params set by a prior Jenkinsfile build).
     * This is the default.
     */
    REPLACE,

    /**
     * Merge plugin parameters with any parameters the Jenkinsfile already declared.
     * Jenkinsfile-only params are preserved above the banner; on a name conflict
     * the plugin's definition takes precedence.
     */
    MERGE_PLUGIN_WINS,

    /**
     * Merge plugin parameters with any parameters the Jenkinsfile already declared.
     * Jenkinsfile params are kept as-is; plugin params are only added for names that
     * the Jenkinsfile does not already define.
     */
    MERGE_JENKINSFILE_WINS,

    /**
     * Inject plugin parameters only when the job does not yet have parameters that
     * were set by a Jenkinsfile build.  Once a build has run and the Jenkinsfile's
     * {@code properties()} call has stored its parameters, subsequent scans will
     * leave those parameters untouched.
     *
     * <p>Detection: the plugin marks its own injections with a
     * {@link MultiBranchHeaderParameter} sentinel.  If the current job has a
     * {@code ParametersDefinitionProperty} that contains <em>no</em> such marker,
     * the parameters are assumed to be Jenkinsfile-owned and are left alone.
     */
    SKIP_IF_JENKINSFILE
}
