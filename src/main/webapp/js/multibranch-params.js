/**
 * Multibranch Pipeline Parameterization Plugin – UI helpers.
 *
 * Hides the "Branch name pattern" field when filter mode is ALL,
 * since the pattern is irrelevant in that case.
 */
(function () {
    'use strict';

    document.addEventListener('DOMContentLoaded', function () {
        // Scope to our strategy config sections only
        var sections = document.querySelectorAll(
            '[data-descriptor*="ParameterizedBranchPropertyStrategy"]');

        sections.forEach(function (section) {
            var modeSelect = section.querySelector('[name="filterMode"]');
            var patternRow = section.querySelector('[field="branchPattern"]');

            if (!modeSelect || !patternRow) return;

            function togglePatternVisibility() {
                var isAll = modeSelect.value === 'ALL';
                patternRow.closest('tr, .jenkins-form-item')
                    .style.display = isAll ? 'none' : '';
            }

            modeSelect.addEventListener('change', togglePatternVisibility);
            togglePatternVisibility(); // run on page load
        });
    });
}());
