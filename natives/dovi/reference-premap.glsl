//!HOOK MAIN
//!BIND HOOKED
//!SAVE WOKA_PQ
//!DESC DV5 capture decoded BT2020 PQ before display mapping
vec4 hook() {
    return HOOKED_texOff(0);
}

//!HOOK OUTPUT
//!BIND WOKA_PQ
//!DESC DV5 emit captured PQ for numerical comparison
vec4 hook() {
    return WOKA_PQ_texOff(0);
}
