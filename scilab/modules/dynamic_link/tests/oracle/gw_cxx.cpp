#include "api_scilab.h"
#include <vector>
extern "C" int sci_gw_cxx(char *fname, void *pvApiCtx) { std::vector<int> v; v.push_back(1); return (int)v.size()-1; }
