/*
A KBase module: kb_prodege
*/

module kb_prodege {
    typedef structure {
        string report_name;
        string report_ref;
    } ReportResults;

    funcdef run_kb_prodege(mapping<string,UnspecifiedObject> params) returns (ReportResults output) authentication required;

};
