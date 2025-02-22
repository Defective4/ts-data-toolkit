function attachLs(page) {
	const inputs = document.getElementsByClassName("page-input");
	for (let i = 0; i < inputs.length; i++) {
		const input = inputs[i];
		if (i == 0)
			input.focus();
		input.value = page;
		input.onkeydown = (e) => {
			if (e.key == "Enter") {
				const target = input.value + ".html";
				window.location.href = target;
			} else if (e.key == "ArrowUp" || e.key == "ArrowDown") {
				const mod = e.key == "ArrowUp" ? 1 : -1;
				window.location.href = (parseInt(page) + mod) + ".html";
			}
		}
	}
}