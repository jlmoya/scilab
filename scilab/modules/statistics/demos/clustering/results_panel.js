// Scilab ( https://www.scilab.org/ ) - This file is part of Scilab
// Copyright (C) 2026 - Dassault Systèmes S.E. - Adeline CARNIS
//
// This file is released under the 3-clause BSD license. See COPYING-BSD.

function renderClustering(id, d) {
    if (id == "dbscan") {
        var html = '<div class="algo-sub">' +
               d.n_clusters + ' cluster(s) \u2022 ' + d.n_noise + ' noise pt(s)' +
               '</div>';
        if (d.n_noise > 0) {
            html += legendItem(d.noise_hex, 'Noise (' + d.n_noise + ' pts)');
        }
    }
    else
    {
        var html = '<div class="algo-sub">' + d.n_clusters + ' cluster(s)</div>';
    }
    
    d.colors = [].concat(d.colors || []);
    d.counts = [].concat(d.counts || []);

    for (var i = 0; i < d.n_clusters; i++) {
        html += legendItem(d.colors[i], 'C' + (i + 1) + ' (' + d.counts[i] + ' pts)');
    }
    show(id, html);
  }

  function legendItem(hex, label) {
    return '<div class="legend-item">' +
           '<span class="legend-dot" style="background:' + hex + '"></span>' +
           '<span>' + label + '</span>' +
           '</div>';
  }

function show(id, html) {
    $("#content-" + id).html(html);
    $("#results-" + id).css("display", "block");
}

  function fromScilab(data) {
    document.getElementById('wait').style.display = 'none';
    if (data.type === 'all') {
      renderClustering('dbscan', data.dbscan);
      renderClustering('meanshift', data.meanshift);
      renderClustering('kmeans', data.kmeans);
    } 
    else 
    {
      renderClustering(data.type, data);
    }
  }
