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
 *   <tr><th>Mode</th><th>At scan time</th><th>After a Jenkinsfile build runs</th></tr>
 *   <tr><td>REPLACE</td>
 *       <td>Always inject plugin params, replacing whatever is there.</td>
 *       <td>Jenkinsfile can overwrite; next scan restores plugin params.</td></tr>
 *   <tr><td>MERGE</td>
 *       <td>Combine Jenkinsfile params with plugin params; plugin wins on name conflict.</td>
 *       <td>Jenkinsfile can overwrite; next scan merges again.</td></tr>
 *   <tr><td>SKIP_IF_JENKINSFILE</td>
 *       <td>Inject only if params were not previously set by a Jenkinsfile build
 *           (detected by absence of the plugin header marker).</td>
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
     * Jenkinsfile-only params are preserved; on a name conflict the plugin's
     * definition takes precedence.
     */
    MERGE,

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
