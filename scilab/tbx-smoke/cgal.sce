// cgal smoke: the native CGAL convex-hull-2 gateway (sci_gateway/c/sci_convex_hull_2.c,
// registered directly via addinter -- see sci_gateway/c/loader.sce's list_functions;
// convex_hull_2 has no macro wrapper, so this call reaches native code
// unconditionally) on a fixed 28-point set, with the golden hull index list
// lifted verbatim from the toolbox's own regression test
// (tests/unit_tests/convex_hull_2.tst).
x = [46. 120. 207. 286. 366. 453. 543. 544. 473. 387. 300. 206. 136. 250. 346. 408. 527. 443. 306. 326. 196. 139. 264. 55. 58. 46. 118. 513.];
y = [36. 34. 37. 40. 38. 40. 35. 102. 102. 98. 93. 96. 167. 172. 101. 179. 198. 252. 183. 148. 172. 256. 259. 258. 167. 109. 104. 253.];
xy = [x; y];
[nhull, ind] = convex_hull_2(xy);
ind1 = int32([1 2 7 8 17 28 23 24 26]);
// Discriminates a real hull computation from a no-op/broken native call: a
// degenerate implementation would not recover exactly these 9 indices (of 28
// candidate points) in this exact cyclic order.
smoke_ok = isequal(nhull, int32(9)) & isequal(ind, ind1);
