//# sourceURL=settings.js
globals.pageView = new Vue({
            el: '#settings',
            data: {
                license: {},
                status: {},
                node: {},
                nodes: []
            }, mounted() {
        this.reloadStatus();
        axios.get("v1/public/license").then(response => { this.license = response.data });
        axios.get("v1/public/me").then(response => { this.node = response.data });
        axios.get("v1/public").then(response => { this.nodes = response.data });
    }, methods: {
        reloadStatus: function () {
            axios.get("v1/public/status").then(response => { this.status = response.data });
        }
    }
        });